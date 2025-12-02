<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('smsCode') displayInfo=false; section>
    <#if section = "header">
        ${msg("codeVerificationTitle")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <div class="kc-info-text">
                    <p>${msg("codeSentTo", phoneNumber!'***')}</p>
                    <p>${msg("enterCodeToProceed")}</p>
                </div>

                <form id="kc-sms-code-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label for="smsCode" class="${properties.kcLabelClass!}">${msg("smsCode")}</label>
                        </div>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input tabindex="1" id="smsCode" class="${properties.kcInputClass!} sms-code-input" name="smsCode"
                                   value="${(smsCode!'')}" type="text"
                                   placeholder="${msg("smsCodePlaceholder")}"
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
                                    <span id="countdown-text">${msg("codeValidFor")} <span id="countdown">5:00</span></span>
                                </div>
                            </div>
                        </div>
                        <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                            <input tabindex="2" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                   name="login" id="kc-login" type="submit" value="${msg("verifyCode")}"/>
                        </div>
                    </div>
                </form>

                <div id="kc-info-wrapper" class="${properties.kcFormOptionsWrapperClass!}">
                    <div class="kc-info-text">
                        <p>${msg("didNotReceiveSms")}</p>
                        <a href="${url.loginRestartFlowUrl!}" id="resend-sms">${msg("resendCode")}</a>
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
            (function() {
                // Odpočítávání času (výchozí 5 minut)
                var timeLeft = 300;
                var countdownElement = document.getElementById('countdown');
                var countdownText = document.getElementById('countdown-text');
                var smsCodeInput = document.getElementById('smsCode');
                var submitButton = document.getElementById('kc-login');
                var form = document.getElementById('kc-sms-code-form');
                var codeExpiredMsg = '${msg("codeExpired")?js_string}';

                function updateCountdown() {
                    var minutes = Math.floor(timeLeft / 60);
                    var seconds = timeLeft % 60;
                    countdownElement.textContent = minutes + ':' + (seconds < 10 ? '0' : '') + seconds;

                    if (timeLeft <= 0) {
                        countdownText.innerHTML = '<span class="countdown-expired">' + codeExpiredMsg + '</span>';
                        smsCodeInput.disabled = true;
                        submitButton.disabled = true;
                    } else {
                        timeLeft--;
                        setTimeout(updateCountdown, 1000);
                    }
                }

                updateCountdown();

                // Auto-submit při zadání kódu (pokud má správnou délku 4-8 číslic)
                smsCodeInput.addEventListener('input', function(e) {
                    // Pouze čísla
                    e.target.value = e.target.value.replace(/\D/g, '');

                    var codeLength = e.target.value.length;
                    // Auto-submit pro 6místný kód (nejběžnější)
                    if (codeLength === 6) {
                        setTimeout(function() {
                            if (smsCodeInput.value.length === 6 && !smsCodeInput.disabled) {
                                form.submit();
                            }
                        }, 500);
                    }
                });

                // Focus na pole s kódem
                smsCodeInput.focus();
            })();
        </script>
    </#if>
</@layout.registrationLayout>