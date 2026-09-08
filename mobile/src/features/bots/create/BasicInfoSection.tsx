interface BasicInfoSectionProps {
    botName: string;
    onBotNameChange: (value: string) => void;
}

function BasicInfoSection({
    botName,
    onBotNameChange,
}: BasicInfoSectionProps) {
    return (
        <article className="content-card">
            <div className="bot-form-section-header">
                <div>
                    <span className="bot-form-step">
                        1
                    </span>

                    <h2 className="content-card-title">
                        Podstawowe informacje
                    </h2>
                </div>

                <p className="content-card-text">
                    Nazwa służy tylko do rozpoznawania
                    bota w panelu.
                </p>
            </div>

            <div className="form-field">
                <label
                    className="form-label"
                    htmlFor="bot-name"
                >
                    Nazwa bota
                </label>

                <input
                    id="bot-name"
                    className="form-input"
                    type="text"
                    value={botName}
                    maxLength={255}
                    placeholder="np. Samsung S25"
                    onChange={(event) => {
                        onBotNameChange(
                            event.target.value,
                        );
                    }}
                />
            </div>
        </article>
    );
}

export default BasicInfoSection;