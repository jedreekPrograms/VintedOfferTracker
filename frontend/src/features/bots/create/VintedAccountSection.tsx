interface VintedAccountSectionProps {
    email: string;
    password: string;

    passwordOptional?: boolean;

    onEmailChange: (value: string) => void;
    onPasswordChange: (value: string) => void;
}

function VintedAccountSection({
    email,
    password,

    passwordOptional = false,

    onEmailChange,
    onPasswordChange,
}: VintedAccountSectionProps) {
    return (
        <article className="content-card">
            <div className="bot-form-section-header">
                <div>
                    <span className="bot-form-step">
                        2
                    </span>

                    <h2 className="content-card-title">
                        Konto Vinted
                    </h2>
                </div>

                <p className="content-card-text">
                    Każdy bot korzysta ze swojego
                    osobnego konta Vinted.
                </p>
            </div>

            <div className="bot-form-grid bot-form-grid-two">
                <div className="form-field">
                    <label
                        className="form-label"
                        htmlFor="vinted-email"
                    >
                        E-mail
                    </label>

                    <input
                        id="vinted-email"
                        className="form-input"
                        type="email"
                        value={email}
                        autoComplete="username"
                        placeholder="konto@example.com"
                        onChange={(event) => {
                            onEmailChange(
                                event.target.value,
                            );
                        }}
                    />
                </div>

                <div className="form-field">
                    <label
                        className="form-label"
                        htmlFor="vinted-password"
                    >
                        {
                            passwordOptional
                                ? "Hasło (opcjonalnie)"
                                : "Hasło"
                        }
                    </label>

                    <input
                        id="vinted-password"
                        className="form-input"
                        type="password"
                        value={password}
                        autoComplete="current-password"
                        placeholder={
                            passwordOptional
                                ? "Pozostaw puste, aby zachować obecne hasło"
                                : "Hasło do konta Vinted"
                        }
                        onChange={(event) => {
                            onPasswordChange(
                                event.target.value,
                            );
                        }}
                    />
                </div>
            </div>

            <div className="information-box">
                {
                    passwordOptional
                        ? (
                            <>
                                Pozostaw pole hasła puste,
                                jeśli nie chcesz zmieniać
                                danych logowania tego bota.
                            </>
                        )
                        : (
                            <>
                                Konto zostanie przypisane wyłącznie
                                do tego konkretnego bota.
                            </>
                        )
                }
            </div>
        </article>
    );
}

export default VintedAccountSection;
