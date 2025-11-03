/**
 * Generic i18n initialization script.
 * Solves internationalization for all pages using a synchronous approach.
 */

// Global flag to track initialization status
window.i18nInitialized = false;

// Initialize i18n and apply translations
window.initializeI18n = function() {
    console.log("Starting synchronous i18n initialization...");
    if (window.i18nInitialized) {
        console.log("i18n is already initialized, skipping.");
        return;
    }

    if (typeof i18n === 'undefined' || typeof window.translations === 'undefined') {
        console.error("i18n object or pre-loaded translations are not defined, cannot initialize.");
        return;
    }

    // i18n.init() is now synchronous and determines the language internally from localStorage and pre-loaded data
    i18n.init();

    console.log("i18n initialization successful.");
    window.i18nInitialized = true;
};

// Function to switch language
window.switchLanguage = function(lang) {
    console.log(`Switching language to: ${lang}`);
    
    // Save the new language choice
    localStorage.setItem('preferredLang', lang);
    
    // Instead of reloading, re-initialize with the new language and update the UI.
    // This provides a smoother experience without a full page refresh.
    i18n.init(lang);
};

// Auto-initialize after the DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    console.log("DOM content loaded, preparing to initialize i18n...");
    
    // Initialize i18n synchronously
    window.initializeI18n();
    
    // Use event delegation on the document to handle clicks on language switch buttons,
    // ensuring they work even if loaded dynamically (e.g., in the navbar).
    document.addEventListener('click', function(event) {
        const langButton = event.target.closest('.lang-select-btn');
        if (langButton) {
            event.preventDefault();
            const selectedLang = langButton.getAttribute('data-lang');
            if (selectedLang) {
                window.switchLanguage(selectedLang);
            }
        }
    });
});

console.log("i18n-init.js (synchronous version) loaded.");
