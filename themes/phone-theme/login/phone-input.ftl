<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('phoneNumber','firstName','lastName') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("phoneInputTitle")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <form id="kc-phone-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label for="phoneNumber" class="${properties.kcLabelClass!}">${msg("phoneNumber")}</label>
                        </div>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input tabindex="1" id="phoneNumber" class="${properties.kcInputClass!}" name="phoneNumber"
                                   value="${(phoneNumber!'')}" type="tel"
                                   placeholder="${msg("phoneNumberPlaceholder")}"
                                   autocomplete="tel"
                                   autofocus
                                   aria-invalid="<#if messagesPerField.existsError('phoneNumber')>true</#if>"
                            />
                            <#if messagesPerField.existsError('phoneNumber')>
                                <span id="input-error-phoneNumber" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${kcSanitize(messagesPerField.getFirstError('phoneNumber'))?no_esc}
                                </span>
                            </#if>
                        </div>
                    </div>

                    <div class="${properties.kcFormGroupClass!}">
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label for="firstName" class="${properties.kcLabelClass!}">${msg("firstName")}</label>
                        </div>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input tabindex="2" id="firstName" class="${properties.kcInputClass!}" name="firstName"
                                   value="${(firstName!'')}" type="text"
                                   autocomplete="given-name"
                                   aria-invalid="<#if messagesPerField.existsError('firstName')>true</#if>"
                            />
                            <#if messagesPerField.existsError('firstName')>
                                <span id="input-error-firstName" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${kcSanitize(messagesPerField.getFirstError('firstName'))?no_esc}
                                </span>
                            </#if>
                        </div>
                    </div>

                    <div class="${properties.kcFormGroupClass!}">
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label for="lastName" class="${properties.kcLabelClass!}">${msg("lastName")}</label>
                        </div>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input tabindex="3" id="lastName" class="${properties.kcInputClass!}" name="lastName"
                                   value="${(lastName!'')}" type="text"
                                   autocomplete="family-name"
                                   aria-invalid="<#if messagesPerField.existsError('lastName')>true</#if>"
                            />
                            <#if messagesPerField.existsError('lastName')>
                                <span id="input-error-lastName" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${kcSanitize(messagesPerField.getFirstError('lastName'))?no_esc}
                                </span>
                            </#if>
                        </div>
                    </div>

                    <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                        <div id="kc-form-options">
                            <div class="${properties.kcFormOptionsWrapperClass!}">
                            </div>
                        </div>
                        <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                            <input tabindex="4" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                   name="login" id="kc-login" type="submit" value="${msg("sendSmsCode")}"/>
                        </div>
                    </div>
                </form>

                <div class="kc-info-text">
                    <p>${msg("phoneInputInfo")}</p>
                    <p>${msg("newUserInfo")}</p>
                </div>
            </div>
        </div>

        <script>
            (function() {
                var phoneInput = document.getElementById('phoneNumber');

                // Auto-formátování telefonního čísla
                phoneInput.addEventListener('input', function(e) {
                    var value = e.target.value;
                    var cursorPos = e.target.selectionStart;

                    // Pokud už začíná +, ponecháme formát
                    if (value.startsWith('+')) {
                        // Pouze očistíme nečíselné znaky kromě + na začátku a mezer
                        var cleaned = '+' + value.substring(1).replace(/[^\d\s]/g, '');
                        // Omezíme na max 20 znaků
                        e.target.value = cleaned.substring(0, 20);
                        return;
                    }

                    // Očistíme pouze na čísla
                    var digits = value.replace(/\D/g, '');

                    // Pokud je to české číslo bez předvolby (9 číslic)
                    if (digits.length === 9 && !digits.startsWith('420')) {
                        digits = '420' + digits;
                    }
                    // Pokud začíná 0 (národní formát)
                    else if (digits.length >= 10 && digits.startsWith('0')) {
                        digits = '420' + digits.substring(1);
                    }

                    // Formátování s mezerami
                    var formatted = '';
                    if (digits.length > 0) {
                        formatted = '+' + digits.substring(0, 3);
                        if (digits.length > 3) {
                            formatted += ' ' + digits.substring(3, 6);
                        }
                        if (digits.length > 6) {
                            formatted += ' ' + digits.substring(6, 9);
                        }
                        if (digits.length > 9) {
                            formatted += ' ' + digits.substring(9, 12);
                        }
                    }

                    e.target.value = formatted;
                });

                // Focus na první pole
                phoneInput.focus();
            })();
        </script>
    </#if>
</@layout.registrationLayout>