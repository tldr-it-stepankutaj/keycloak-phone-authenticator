<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('firstName','lastName'); section>
    <#if section = "header">
        ${msg("userDetailsTitle")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <div class="kc-info-text">
                    <p>${msg("userDetailsInfo")}</p>
                </div>

                <form id="kc-user-details-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label for="firstName" class="${properties.kcLabelClass!}">${msg("firstName")}</label>
                        </div>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input tabindex="1" id="firstName" class="${properties.kcInputClass!}" name="firstName"
                                   value="${(firstName!'')}" type="text"
                                   autocomplete="given-name"
                                   autofocus
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
                            <input tabindex="2" id="lastName" class="${properties.kcInputClass!}" name="lastName"
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
                        <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                            <input tabindex="3" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                   name="login" id="kc-login" type="submit" value="${msg("proceed")}"/>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
