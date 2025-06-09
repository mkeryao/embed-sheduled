/**
 * 通用的i18n初始化脚本
 * 解决所有页面的国际化问题
 */

// 全局初始化状态追踪
window.i18nInitialized = false;

// 初始化i18n并应用翻译
window.initializeI18n = function() {
    console.log("开始初始化i18n...");
    return new Promise((resolve, reject) => {
        if (window.i18nInitialized) {
            console.log("i18n已经初始化，跳过");
            resolve(true);
            return;
        }
        
        if (typeof i18n === 'undefined') {
            console.error("i18n对象未定义，无法初始化");
            reject("i18n对象未定义");
            return;
        }
        
        // 使用绝对路径方式加载翻译文件
        const originalLoadTranslations = i18n.loadTranslations;
        i18n.loadTranslations = async function(lang) {
            try {
                console.log(`加载 ${lang} 翻译...`);
                const basePath = window.location.pathname.substring(0, window.location.pathname.lastIndexOf('/') + 1);
                const translationUrl = `${window.location.origin}${basePath}locales/${lang}.json?v=${new Date().getTime()}`;
                console.log(`翻译文件URL: ${translationUrl}`);
                
                const response = await fetch(translationUrl);
                if (!response.ok) {
                    console.error(`无法加载 ${lang} 的翻译. 状态: ${response.status}`);
                    if (lang !== this.defaultLang) {
                        console.warn(`回退到默认语言: ${this.defaultLang}`);
                        return this.loadTranslations(this.defaultLang);
                    }
                    this.translations[lang] = {};
                    return false;
                }
                
                this.translations[lang] = await response.json();
                this.currentLang = lang;
                localStorage.setItem('preferredLang', lang);
                console.log(`${lang} 的翻译加载成功`);
                return true;
            } catch (error) {
                console.error(`加载 ${lang} 翻译时出错:`, error);
                if (lang !== this.defaultLang) {
                    console.warn(`回退到默认语言: ${this.defaultLang}`);
                    return this.loadTranslations(this.defaultLang);
                }
                this.translations[lang] = {};
                return false;
            }
        };

        // 执行初始化
        const preferredLang = localStorage.getItem('preferredLang') || navigator.language.split('-')[0] || i18n.defaultLang;
        let langToLoad = (preferredLang === 'zh') ? 'zh' : i18n.defaultLang;
        
        console.log(`将加载语言: ${langToLoad}`);
        i18n.init(langToLoad).then(() => {
            console.log("i18n初始化成功");
            window.i18nInitialized = true;
            resolve(true);
        }).catch(err => {
            console.error("i18n初始化失败:", err);
            reject(err);
        });
    });
};

// 语言切换函数
window.switchLanguage = function(lang) {
    console.log(`切换语言到: ${lang}`);
    
    // 保存当前语言选择
    localStorage.setItem('preferredLang', lang);
    
    // 重新初始化i18n
    if (typeof i18n !== 'undefined') {
        i18n.init(lang).then(() => {
            console.log(`语言已切换到 ${lang}`);
            // 刷新页面以应用新语言
            window.location.reload();
        }).catch(err => {
            console.error("切换语言失败:", err);
        });
    } else {
        console.error("i18n对象未定义，无法切换语言");
    }
};

// DOM加载完成后自动初始化
document.addEventListener('DOMContentLoaded', function() {
    console.log("DOM加载完成，准备初始化i18n...");
    
    // 初始化i18n
    window.initializeI18n().catch(err => {
        console.error("无法初始化i18n:", err);
    });
    
    // 为语言切换按钮添加事件监听
    document.querySelectorAll('.lang-select-btn').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            const selectedLang = this.getAttribute('data-lang');
            window.switchLanguage(selectedLang);
        });
    });
});

console.log("i18n-init.js 已加载");
