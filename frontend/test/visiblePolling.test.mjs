import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createVisiblePoller } from '../src/utils/visiblePolling.ts';

async function settle() {
    for (let i = 0; i < 20; i++) await Promise.resolve();
}

function environment(t, visibility = 'visible') {
    const document = new EventTarget();
    document.visibilityState = visibility;
    const original = Object.getOwnPropertyDescriptor(globalThis, 'document');
    Object.defineProperty(globalThis, 'document', { configurable: true, value: document });
    const cleanups = [];
    t.after(() => {
        for (const cleanup of cleanups) cleanup();
        if (original) Object.defineProperty(globalThis, 'document', original);
        else delete globalThis.document;
    });
    const timers = new Map();
    let timerId = 0;
    t.mock.method(globalThis, 'setTimeout', (callback, delay) => {
        timers.set(++timerId, { callback, delay });
        return timerId;
    });
    t.mock.method(globalThis, 'clearTimeout', id => timers.delete(id));
    return {
        timers,
        stopAfter(poller) { cleanups.push(() => poller.stop()); },
        visibility(state) {
            document.visibilityState = state;
            document.dispatchEvent(new Event('visibilitychange'));
        },
        tick() {
            const pending = [...timers.values()];
            timers.clear();
            for (const timer of pending) timer.callback();
        },
    };
}

function requests() {
    const calls = [];
    return {
        calls,
        task: signal => new Promise((resolve, reject) => calls.push({ signal, resolve, reject })),
    };
}

test('manual refresh after a session change waits for a fresh snapshot without overlapping requests', async t => {
    const env = environment(t);
    const { calls, task } = requests();
    const poller = createVisiblePoller(task, 5000);
    env.stopAfter(poller);
    await settle();
    assert.equal(calls.length, 1);
    const firstRefresh = poller.refresh();
    assert.equal(poller.refresh(), firstRefresh, 'concurrent refreshes share one pending follow-up');
    let finished = false;
    void firstRefresh.then(() => { finished = true; });
    assert.equal(calls.length, 1);
    calls[0].resolve();
    await settle();
    assert.equal(calls.length, 2);
    assert.equal(finished, false, 'the old pre-click snapshot cannot complete the refresh');
    assert.equal(env.timers.size, 0);
    calls[1].resolve();
    await firstRefresh;
    assert.equal(env.timers.size, 1);
    assert.equal([...env.timers.values()][0].delay, 5000);
    env.tick();
    await settle();
    assert.equal(calls.length, 3);
    calls[2].resolve();
});

test('hidden tabs abort reads and resume immediately when visible', async t => {
    const env = environment(t, 'hidden');
    const { calls, task } = requests();
    const poller = createVisiblePoller(task, 5000);
    env.stopAfter(poller);
    await poller.refresh();
    assert.equal(calls.length, 0);
    env.visibility('visible');
    await settle();
    assert.equal(calls.length, 1);
    env.visibility('hidden');
    assert.equal(calls[0].signal.aborted, true);
    env.visibility('visible');
    await settle();
    assert.equal(calls.length, 1, 'resume waits for aborted request cleanup');
    calls[0].reject(new DOMException('Hidden', 'AbortError'));
    await settle();
    assert.equal(calls.length, 2);
    calls[1].resolve();
    await settle();
    env.visibility('hidden');
    assert.equal(env.timers.size, 0);
});

test('unmount aborts an active read and prevents queued or late refreshes', async t => {
    const env = environment(t);
    const { calls, task } = requests();
    const poller = createVisiblePoller(task, 5000);
    await settle();
    const refresh = poller.refresh();
    poller.stop();
    assert.equal(calls[0].signal.aborted, true);
    calls[0].resolve();
    await refresh;
    env.visibility('hidden');
    env.visibility('visible');
    await poller.refresh();
    assert.equal(calls.length, 1);
    assert.equal(env.timers.size, 0);
});

test('immediate cleanup prevents a request from starting (React Strict Mode)', async t => {
    const env = environment(t);
    const { calls, task } = requests();
    const poller = createVisiblePoller(task, 5000);
    poller.stop();
    await settle();
    assert.equal(calls.length, 0);
    assert.equal(env.timers.size, 0);
});

test('a failed read schedules another poll and does not run on top of a slow request', async t => {
    const env = environment(t);
    const { calls, task } = requests();
    const poller = createVisiblePoller(task, 5000);
    env.stopAfter(poller);
    await settle();
    env.tick();
    await settle();
    assert.equal(calls.length, 1);
    calls[0].reject(new Error('Network failed'));
    await settle();
    assert.equal(env.timers.size, 1);
    env.tick();
    await settle();
    assert.equal(calls.length, 2);
    calls[1].resolve();
});
