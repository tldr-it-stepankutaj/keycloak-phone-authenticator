<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('phoneNumber','firstName','lastName') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("phoneInputTitle")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <form id="kc-phone-form" class="${properties.kcFormClass!}" action="${url}" method="post">
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
            // Auto-formátování telefonního čísla
            document.getElementById('phoneNumber').addEventListener('input', function(e) {
                let value = e.target.value.replace(/\D/g, '');

                if (value.length > 0 && !value.startsWith('420')) {
                    if (value.length === 9) {
                        value = '420' + value;
                    } else if (value.length === 10 && value.startsWith('0')) {
                        value = '420' + value.substring(1);
                    }
                }

                if (value.length > 3) {
                    value = '+' + value.substring(0, 3) + ' ' + value.substring(3);
                }
                if (value.length > 8) {
                    value = value.substring(0, 8) + ' ' + value.substring(8);
                }
                if (value.length > 12) {
                    value = value.substring(0, 12) + ' ' + value.substring(12);
                }

                e.target.value = value.substring(0, 17); // Max délka
            });

            // Focus na první pole
            document.getElementById('phoneNumber').focus();
        </script>
    </#if>
</@layout.registrationLayout>