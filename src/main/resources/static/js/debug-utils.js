/**
 * debug-utils.js
 * 提供调试工具和帮助函数
 */

// 避免重复初始化
if (typeof window.debugUtilsInitialized === 'undefined') {
    console.log('初始化调试工具...');
    window.debugUtilsInitialized = true;
    
    // 记录初始化时间
    window.debugStartTime = new Date();
    
    // 性能度量函数
    window.measurePerformance = function(label, callback) {
        if (!window.debugMode) {
            return callback();
        }
        
        console.log(`开始测量: ${label}`);
        const start = performance.now();
        try {
            return callback();
        } finally {
            const end = performance.now();
            console.log(`${label} - 耗时: ${(end - start).toFixed(2)}ms`);
        }
    };
    
    // 创建调试面板
    window.createDebugPanel = function() {
        if (document.getElementById('debug-panel')) {
            return; // 面板已存在
        }
        
        const panel = document.createElement('div');
        panel.id = 'debug-panel';
        panel.style.cssText = `
            position: fixed;
            bottom: 0;
            right: 0;
            width: 300px;
            background-color: rgba(0, 0, 0, 0.8);
            color: #00ff00;
            font-family: monospace;
            font-size: 12px;
            z-index: 9999;
            padding: 10px;
            border-top-left-radius: 5px;
            display: none;
            max-height: 400px;
            overflow-y: auto;
        `;
        
        const header = document.createElement('div');
        header.style.cssText = `
            display: flex;
            justify-content: space-between;
            border-bottom: 1px solid #555;
            padding-bottom: 5px;
            margin-bottom: 5px;
        `;
        
        const title = document.createElement('span');
        title.textContent = '调试面板';
        
        const closeButton = document.createElement('button');
        closeButton.textContent = 'X';
        closeButton.style.cssText = `
            background: none;
            border: none;
            color: red;
            font-weight: bold;
            cursor: pointer;
        `;
        closeButton.onclick = function() {
            panel.style.display = 'none';
        };
        
        header.appendChild(title);
        header.appendChild(closeButton);
        panel.appendChild(header);
        
        const content = document.createElement('div');
        content.id = 'debug-content';
        panel.appendChild(content);
        
        const footer = document.createElement('div');
        footer.style.cssText = `
            margin-top: 10px;
            text-align: center;
            border-top: 1px solid #555;
            padding-top: 5px;
        `;
        
        const clearButton = document.createElement('button');
        clearButton.textContent = '清除日志';
        clearButton.style.cssText = `
            background: #333;
            color: white;
            border: 1px solid #555;
            border-radius: 3px;
            margin-right: 10px;
            cursor: pointer;
        `;
        clearButton.onclick = function() {
            document.getElementById('debug-content').innerHTML = '';
        };
        
        const toggleButton = document.createElement('button');
        toggleButton.textContent = window.debugMode ? '关闭调试模式' : '启用调试模式';
        toggleButton.style.cssText = `
            background: #333;
            color: white;
            border: 1px solid #555;
            border-radius: 3px;
            cursor: pointer;
        `;
        toggleButton.onclick = function() {
            window.toggleDebugMode();
            this.textContent = window.debugMode ? '关闭调试模式' : '启用调试模式';
            window.addDebugMessage('调试模式: ' + (window.debugMode ? '已启用' : '已禁用'));
        };
        
        footer.appendChild(clearButton);
        footer.appendChild(toggleButton);
        panel.appendChild(footer);
        
        document.body.appendChild(panel);
        
        // 添加调试信息显示函数
        window.addDebugMessage = function(message) {
            const content = document.getElementById('debug-content');
            if (!content) return;
            
            const timestamp = new Date().toLocaleTimeString();
            const messageElement = document.createElement('div');
            messageElement.innerHTML = `<span style="color:#999;">[${timestamp}]</span> ${message}`;
            content.appendChild(messageElement);
            
            // 自动滚动到底部
            content.scrollTop = content.scrollHeight;
        };
        
        // 重写console.log等方法，将输出同时显示在调试面板
        const originalLog = console.log;
        const originalWarn = console.warn;
        const originalError = console.error;
        
        console.log = function(...args) {
            originalLog.apply(console, args);
            if (window.debugMode) {
                window.addDebugMessage('<span style="color:#00ff00;">' + args.join(' ') + '</span>');
            }
        };
        
        console.warn = function(...args) {
            originalWarn.apply(console, args);
            if (window.debugMode) {
                window.addDebugMessage('<span style="color:#ffff00;">' + args.join(' ') + '</span>');
            }
        };
        
        console.error = function(...args) {
            originalError.apply(console, args);
            window.addDebugMessage('<span style="color:#ff0000;">' + args.join(' ') + '</span>');
        };
        
        return panel;
    };
    
    // 添加快捷键显示调试面板
    document.addEventListener('keydown', function(e) {
        // Ctrl+Shift+D显示调试面板
        if (e.ctrlKey && e.shiftKey && e.key === 'D') {
            e.preventDefault();
            const panel = document.getElementById('debug-panel') || window.createDebugPanel();
            panel.style.display = panel.style.display === 'none' ? 'block' : 'none';
            
            if (panel.style.display === 'block') {
                window.addDebugMessage('调试面板已打开');
                window.addDebugMessage('页面启动时间: ' + window.debugStartTime.toLocaleString());
                window.addDebugMessage('调试模式: ' + (window.debugMode ? '已启用' : '已禁用'));
                
                // 显示一些有用的调试信息
                window.addDebugMessage('浏览器: ' + navigator.userAgent);
                window.addDebugMessage('屏幕分辨率: ' + window.screen.width + 'x' + window.screen.height);
                window.addDebugMessage('窗口大小: ' + window.innerWidth + 'x' + window.innerHeight);
            }
        }
    });
    
    console.log('调试工具初始化完成，使用 Ctrl+Shift+D 显示调试面板');
}
