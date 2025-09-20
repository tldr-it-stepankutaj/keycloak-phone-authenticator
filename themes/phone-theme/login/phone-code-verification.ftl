<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('smsCode') displayInfo=false; section>
    <#if section = "header">
        Ověření SMS kódu
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <div class="kc-info-text">
                    <p>Na telefon <strong>${phoneNumber!'***'}</strong> byl odeslán ověřovací kód.</p>
                    <p>Zadejte prosím kód pro dokončení přihlášení.</p>
                </div>

                <form id="kc-sms-code-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label for="smsCode" class="${properties.kcLabelClass!}">SMS kód</label>
                        </div>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input tabindex="1" id="smsCode" class="${properties.kcInputClass!} sms-code-input" name="smsCode"
                                   value="${(smsCode!'')}" type="text"
                                   placeholder="123456"
                                   autocomplete="one-time-code"
                                   autofocus
                                   maxlength="8"
                                   pattern="[0-9]*"
                                   inputmode="numeric"
                                   aria-invalid="<#if messagesPerField.existsError('smsCode')>true</#if>"
                            />
                            <#if messagesPerField.existsError('smsCode')>
                                <span id="input-error-smsCode" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${kcSanitize(messagesPerField.getFirstError('smsCode'))?no_esc}
                                </span>
                            </#if>
                        </div>
                    </div>

                    <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                        <div id="kc-form-options">
                            <div class="${properties.kcFormOptionsWrapperClass!}">
                                <div class="countdown-timer">
                                    <span id="countdown-text">Kód je platný ještě <span id="countdown">5:00</span></span>
                                </div>
                            </div>
                        </div>
                        <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                            <input tabindex="2" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                   name="login" id="kc-login" type="submit" value="Ověřit kód"/>
                        </div>
                    </div>
                </form>

                <div id="kc-info-wrapper" class="${properties.kcFormOptionsWrapperClass!}">
                    <div class="kc-info-text">
                        <p>Neobdrželi jste SMS?</p>
                        <a href="${url.loginRestartFlowUrl}" id="resend-sms">Požádat o nový kód</a>
                    </div>
                </div>
            </div>
        </div>

        <style>
            .sms-code-input {
                text-align: center;
                font-size: 1.5rem;
                letter-spacing: 0.5rem;
                font-weight: bold;
            }

            .countdown-timer {
                text-align: center;
                margin: 10px 0;
                font-size: 0.9rem;
                color: #666;
            }

            .countdown-expired {
                color: #d32f2f;
                font-weight: bold;
            }

            #resend-sms {
                color: #1976d2;
                text-decoration: none;
                font-weight: 500;
            }

            #resend-sms:hover {
                text-decoration: underline;
            }
        </style>

        <script>
            // Odpočítávání času
            let timeLeft = 300; // 5 minut v sekundách
            const countdownElement = document.getElementById('countdown');
            const countdownText = document.getElementById('countdown-text');

            function updateCountdown() {
                const minutes = Math.floor(timeLeft / 60);
                const seconds = timeLeft % 60;
                countdownElement.textContent = minutes + ':' + (seconds < 10 ? '0' : '') + seconds;

                if (timeLeft <= 0) {
                    countdownText.innerHTML = '<span class="countdown-expired">Kód vypršel</span>';
                    document.getElementById('smsCode').disabled = true;
                    document.getElementById('kc-login').disabled = true;
                } else {
                    timeLeft--;
                    setTimeout(updateCountdown, 1000);
                }
            }

            updateCountdown();

            // Auto-submit při zadání kódu (pokud má správnou délku)
            document.getElementById('smsCode').addEventListener('input', function(e) {
                // Pouze čísla
                e.target.value = e.target.value.replace(/\D/g, '');

                // Auto-submit pro 6místný kód
                if (e.target.value.length === 6) {
                    setTimeout(() => {
                        if (e.target.value.length === 6) {
                            document.getElementById('kc-sms-code-form').submit();
                        }
                    }, 500);
                }
            });

            // Focus na pole s kódem
            document.getElementById('smsCode').focus();
        </script>
    </#if>
</@layout.registrationLayout>