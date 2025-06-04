/**
 * workflow-safe-reload.js
 * 提供页面防无限刷新保护
 */

(function() {
    // 检查是否存在刷新循环
    window.addEventListener('load', function() {
        console.log('workflow-safe-reload.js 正在检查页面刷新状态');
        
        // 获取最后刷新时间
        const lastReload = sessionStorage.getItem('lastReload');
        const now = Date.now();
        
        // 如果上次刷新时间存在且与当前时间间隔小于2秒，表示可能存在刷新循环
        if (lastReload && (now - parseInt(lastReload)) < 2000) {
            console.warn('检测到可能的刷新循环! 激活安全模式');
            
            // 记录刷新计数
            let reloadCount = parseInt(sessionStorage.getItem('reloadCount') || '0');
            reloadCount++;
            sessionStorage.setItem('reloadCount', reloadCount);
            
            // 如果刷新次数过多，启用安全模式
            if (reloadCount >= 3) {
                console.error('过多的页面刷新，正在激活安全模式');
                sessionStorage.setItem('safeMode', 'true');
                
                // 显示错误信息
                setTimeout(function() {
                    const errorDiv = document.createElement('div');
                    errorDiv.className = 'component-error';
                    errorDiv.style.position = 'fixed';
                    errorDiv.style.top = '10px';
                    errorDiv.style.left = '10px';
                    errorDiv.style.right = '10px';
                    errorDiv.style.zIndex = '9999';
                    errorDiv.innerHTML = `
                        <h5>检测到页面刷新循环</h5>
                        <p>系统已启用安全模式，防止浏览器崩溃。</p>
                        <p>问题可能与工作流组件加载顺序有关。</p>
                        <button class="btn btn-sm btn-danger" id="force-reload-btn">强制重新加载一次</button>
                    `;
                    document.body.appendChild(errorDiv);
                    
                    // 绑定强制重新加载按钮
                    document.getElementById('force-reload-btn').addEventListener('click', function() {
                        sessionStorage.removeItem('safeMode');
                        sessionStorage.removeItem('reloadCount');
                        sessionStorage.setItem('lastReload', Date.now());
                        window.location.reload(true); // 强制从服务器重新加载
                    });
                }, 500);
                
                // 监听并拦截任何可能导致自动刷新的函数
                const originalReload = window.location.reload;
                window.location.reload = function() {
                    console.warn('安全模式: 阻止页面自动刷新');
                    return false;
                };
            }
        } else {
            // 重置计数
            if (parseInt(sessionStorage.getItem('reloadCount') || '0') < 3) {
                sessionStorage.removeItem('reloadCount');
            }
        }
        
        // 更新最后刷新时间
        sessionStorage.setItem('lastReload', now);
    });
    
    // 如果在安全模式下，阻止页面上的任何reload()调用
    if (sessionStorage.getItem('safeMode') === 'true') {
        console.warn('页面以安全模式加载，将阻止自动刷新');
        
        // 重写location.reload
        const originalReload = window.location.reload;
        window.location.reload = function() {
            console.warn('安全模式: 阻止页面自动刷新');
            return false;
        };
    }
})();
