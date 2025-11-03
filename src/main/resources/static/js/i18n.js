const i18n = {
    defaultLang: 'zh',
    currentLang: 'zh',
    translations: {},

    translate(key, fallback = '') {
        const keys = key.split('.');
        let currentTrans = this.translations[this.currentLang] || {};
        let result = currentTrans;

        for (const k of keys) {
            if (result && typeof result === 'object' && k in result) {
                result = result[k];
            } else {
                // Fallback to default language
                if (this.currentLang !== this.defaultLang) {
                    let defaultTrans = this.translations[this.defaultLang] || {};
                    let defaultResult = defaultTrans;
                    for (const k_fb of keys) {
                        if (defaultResult && typeof defaultResult === 'object' && k_fb in defaultResult) {
                            defaultResult = defaultResult[k_fb];
                        } else {
                            return fallback || key; // Not found in default lang either
                        }
                    }
                    if (typeof defaultResult === 'string') return defaultResult;
                }
                return fallback || key; // Not found in current, and current is default or default has no key
            }
        }
        return typeof result === 'string' ? result : (fallback || key);
    },

    applyTranslations(container) {
        const rootElement = container || document;
        if (!this.translations[this.currentLang] || Object.keys(this.translations[this.currentLang]).length === 0) {
            console.error(`Translations for '${this.currentLang}' are not loaded or empty. UI elements will show keys or fallback text.`);
            // Do not attempt to reload here; init should handle initial load failures.
            // Fallback to rendering keys for all elements.
        }
        console.log(`Applying translations for ${this.currentLang}. Translation data available:`, !!(this.translations[this.currentLang] && Object.keys(this.translations[this.currentLang]).length > 0));

        rootElement.querySelectorAll('[data-i18n-key], [data-i18n-key-placeholder], [data-i18n-key-title]').forEach(element => {
            const mainKey = element.getAttribute('data-i18n-key');
            const placeholderKey = element.getAttribute('data-i18n-key-placeholder');
            const titleKey = element.getAttribute('data-i18n-key-title');

            if (mainKey) {
                const translation = this.translate(mainKey, mainKey); // Fallback to key
                if (element.tagName === 'TITLE') {
                    document.title = translation;
                } else if (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA') {
                    if (element.type === 'submit' || element.type === 'button') {
                        element.value = translation;
                    } else if (!placeholderKey && element.placeholder !== undefined) { // Only if no specific placeholder key
                        element.placeholder = translation;
                    } else {
                         // If it's not a button and not a placeholder, set textContent if it's not an input that shows content another way
                         // This case might be rare for inputs with data-i18n-key directly for textContent
                    }
                } else {
                    element.textContent = translation;
                }
            }

            if (placeholderKey) {
                element.placeholder = this.translate(placeholderKey, placeholderKey); // Fallback to key
            }

            if (titleKey) {
                element.title = this.translate(titleKey, titleKey); // Fallback to key
            }
        });
        console.log("Translations applied for language:", this.currentLang);
    },

    init(initialLang = null) {
        // Translations are now pre-loaded via <script> tags into window.translations
        this.translations = window.translations || {};

        const preferredLang = initialLang || localStorage.getItem('preferredLang') || navigator.language.split('-')[0] || this.defaultLang;
        this.currentLang = (this.translations[preferredLang]) ? preferredLang : this.defaultLang;

        if (!this.translations[this.currentLang]) {
            console.error(`Translations for '${this.currentLang}' not found in pre-loaded data. UI will display keys.`);
        } else {
            console.log(`Using pre-loaded translations for '${this.currentLang}'`);
        }
        localStorage.setItem('preferredLang', this.currentLang);
        this.applyTranslations();
        this.updateLanguageSwitcherState(this.currentLang);
    },

    updateLanguageSwitcherState(lang) {
         // This function might be called before the navbar (and switcher) is fully loaded via jQuery.
         // So, ensure it runs after DOM is ready or is re-called.
         $(document).ready(() => { // Ensure DOM is ready for switcher updates
            const langSwitcherDropdownButton = document.getElementById('languageDropdown');
            if (langSwitcherDropdownButton) {
                // Update the button text if it shows the current language, e.g. "English" or "中文"
                // This part depends on how you want to display the current language in the switcher.
                // For now, we just ensure the correct item in dropdown might be marked active.
            }
            document.querySelectorAll('.lang-select-btn').forEach(btn => {
                if (btn.getAttribute('data-lang') === lang) {
                    btn.classList.add('active');
                    // If you want to update the main dropdown button text:
                    // if(langSwitcherDropdownButton) langSwitcherDropdownButton.textContent = btn.textContent;
                } else {
                    btn.classList.remove('active');
                }
            });
         });
    }
};

// console.log("i18n.js overwritten and loaded"); // Commented out or removed
