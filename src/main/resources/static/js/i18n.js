// js/i18n.js
const i18n = {
    defaultLang: 'en',
    currentLang: 'en',
    translations: {},

    async loadTranslations(lang) {
        try {
            const response = await fetch(`locales/${lang}.json?v=${new Date().getTime()}`); // Cache busting for dev
            if (!response.ok) {
                console.error(`Could not load translations for ${lang}. Status: ${response.status}`);
                if (lang !== this.defaultLang) { // Fallback to default if specific lang fails
                    console.warn(`Falling back to default language: ${this.defaultLang}`);
                    return this.loadTranslations(this.defaultLang);
                }
                this.translations[lang] = {}; // Ensure translations[lang] exists to prevent errors
                return false;
            }
            this.translations[lang] = await response.json();
            this.currentLang = lang;
            localStorage.setItem('preferredLang', lang);
            console.log(`Translations loaded for ${lang}`);
            return true;
        } catch (error) {
            console.error(`Error loading translations for ${lang}:`, error);
            if (lang !== this.defaultLang) {
               console.warn(`Falling back to default language: ${this.defaultLang} after error.`);
               return this.loadTranslations(this.defaultLang);
            }
            this.translations[lang] = {}; // Ensure translations[lang] exists
            return false;
        }
    },

    translate(key, fallback = '') {
        const keys = key.split('.');
        let currentTranslations = this.translations[this.currentLang] || {};
        let result = currentTranslations;

        for (const k of keys) {
            if (result && typeof result === 'object' && k in result) {
                result = result[k];
            } else {
                // Fallback to default language if key not found in current
                if (this.currentLang !== this.defaultLang && this.translations[this.defaultLang]) {
                    let defaultLangTranslations = this.translations[this.defaultLang] || {};
                    let defaultLangResult = defaultLangTranslations;
                    for (const k_fb of keys) {
                        if (defaultLangResult && typeof defaultLangResult === 'object' && k_fb in defaultLangResult) {
                            defaultLangResult = defaultLangResult[k_fb];
                        } else {
                            return fallback || key; // Key not found in default either
                        }
                    }
                    if (typeof defaultLangResult === 'string') return defaultLangResult;
                }
                return fallback || key; // Key not found
            }
        }
        return typeof result === 'string' ? result : (fallback || key);
    },

    applyTranslations() {
        if (!this.translations[this.currentLang]) {
            console.warn(`No translations loaded for ${this.currentLang}. Cannot apply.`);
            // Attempt to load default if current lang's translations are missing
            if (this.currentLang !== this.defaultLang) {
                console.warn(`Attempting to load default translations (${this.defaultLang}) and re-apply.`);
                this.loadTranslations(this.defaultLang).then(() => this.applyTranslations());
            }
            return;
        }
        document.querySelectorAll('[data-i18n-key]').forEach(element => {
            const key = element.getAttribute('data-i18n-key');
            const translation = this.translate(key);

            if (element.tagName === 'TITLE') {
                document.title = translation;
            } else if (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA') {
                if (element.placeholder) {
                    element.placeholder = translation;
                }
                if (element.type === 'submit' || element.type === 'button') {
                    element.value = translation;
                }
            } else {
                element.textContent = translation;
            }
        });
        console.log("Translations applied for language:", this.currentLang);
    },

    async init(initialLang = null) {
        const preferredLang = initialLang || localStorage.getItem('preferredLang') || navigator.language.split('-')[0] || this.defaultLang;
        let langToLoad = (preferredLang === 'zh') ? 'zh' : this.defaultLang;

        await this.loadTranslations(langToLoad);
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

console.log("i18n.js overwritten and loaded");
