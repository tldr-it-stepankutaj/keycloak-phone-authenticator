<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('phoneNumber'); section>
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

                    <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                        <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                            <input tabindex="2" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                   name="login" id="kc-login" type="submit" value="${msg("sendSmsCode")}"/>
                        </div>
                    </div>
                </form>

                <div class="kc-info-text">
                    <p>${msg("phoneInputInfo")}</p>
                </div>
            </div>
        </div>

        <script>
            (function() {
                var phoneInput = document.getElementById('phoneNumber');

                // Keep only characters that make sense in a phone number. The server normalizes the value
                // (including the realm's default calling code), so no country-specific formatting here.
                phoneInput.addEventListener('input', function(e) {
                    var value = e.target.value;
                    var cleaned = value.replace(/[^\d\s()+-]/g, '');
                    // '+' is allowed only as the first character
                    cleaned = cleaned.charAt(0) + cleaned.substring(1).replace(/\+/g, '');
                    e.target.value = cleaned.substring(0, 24);
                });

                phoneInput.focus();
            })();
        </script>
    </#if>
</@layout.registrationLayout>
