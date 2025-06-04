/**
 * auth-loop-prevention.js
 * 登录页和任务页之间跳转循环的防护措施
 */

(function() {
    // 在页面加载时检测是否存在认证循环跳转
    window.addEventListener('load', function() {
        console.log('auth-loop-prevention.js 正在检查认证循环');
        
        // 获取上次跳转时间和页面
        const lastAuthRedirect = sessionStorage.getItem('lastAuthRedirect');
        const lastAuthPage = sessionStorage.getItem('lastAuthPage');
        const currentPage = window.location.pathname;
        const now = Date.now();
        
        // 记录本次访问
        sessionStorage.setItem('lastAuthRedirect', now);
        sessionStorage.setItem('lastAuthPage', currentPage);
        
        // 如果上次跳转时间存在且与当前时间间隔小于2秒，并且页面不同（在login和tasks之间跳转）
        if (lastAuthRedirect && 
            (now - parseInt(lastAuthRedirect)) < 2000 && 
            lastAuthPage !== currentPage &&
            (currentPage.includes('login.html') || currentPage.includes('tasks.html')) && 
            (lastAuthPage.includes('login.html') || lastAuthPage.includes('tasks.html'))) {
                
            console.warn('检测到登录/任务页面之间的循环跳转!');
            
            // 记录循环跳转计数
            let authLoopCount = parseInt(sessionStorage.getItem('authLoopCount') || '0');
            authLoopCount++;
            sessionStorage.setItem('authLoopCount', authLoopCount);
            
            // 如果跳转次数过多，激活防护措施
            if (authLoopCount >= 3) {
                console.error('过多的认证页面跳转，激活认证循环防护');
                sessionStorage.setItem('authSafeMode', 'true');
                
                // 停止任何进一步的跳转
                const originalAssign = window.location.assign;
                const originalReplace = window.location.replace;
                const originalHref = Object.getOwnPropertyDescriptor(window.location, 'href');
                
                // 覆盖页面跳转方法
                window.location.assign = function(url) {
                    if (url.includes('login.html') || url.includes('tasks.html')) {
                        console.warn('安全模式: 阻止跳转到 ' + url);
                        showAuthLoopError();
                        return;
                    }
                    return originalAssign.call(window.location, url);
                };
                
                window.location.replace = function(url) {
                    if (url.includes('login.html') || url.includes('tasks.html')) {
                        console.warn('安全模式: 阻止替换页面为 ' + url);
                        showAuthLoopError();
                        return;
                    }
                    return originalReplace.call(window.location, url);
                };
                
                // 拦截设置href的操作
                Object.defineProperty(window.location, 'href', {
                    get: function() {
                        return originalHref.get.call(window.location);
                    },
                    set: function(url) {
                        if (url.includes('login.html') || url.includes('tasks.html')) {
                            console.warn('安全模式: 阻止通过href跳转到 ' + url);
                            showAuthLoopError();
                            return;
                        }
                        return originalHref.set.call(window.location, url);
                    }
                });
                
                // 显示错误提示
                showAuthLoopError();
            }
        } else if (parseInt(sessionStorage.getItem('authLoopCount') || '0') < 3) {
            // 如果没有循环或循环计数较低，重置计数
            sessionStorage.removeItem('authLoopCount');
        }
    });
    
    // 显示认证循环错误
    function showAuthLoopError() {
        // 如果已经显示了错误，则不重复显示
        if (document.getElementById('auth-loop-error')) {
            return;
        }
        
        // 创建错误信息元素
        const errorDiv = document.createElement('div');
        errorDiv.id = 'auth-loop-error';
        errorDiv.className = 'auth-loop-error';
        errorDiv.style.position = 'fixed';
        errorDiv.style.top = '50%';
        errorDiv.style.left = '50%';
        errorDiv.style.transform = 'translate(-50%, -50%)';
        errorDiv.style.backgroundColor = '#f8d7da';
        errorDiv.style.color = '#721c24';
        errorDiv.style.padding = '20px';
        errorDiv.style.borderRadius = '5px';
        errorDiv.style.boxShadow = '0 0 10px rgba(0,0,0,0.5)';
        errorDiv.style.zIndex = '9999';
        errorDiv.style.maxWidth = '80%';
        errorDiv.style.textAlign = 'center';
        
        // 添加错误内容
        errorDiv.innerHTML = `
            <h3 style="margin-top:0">检测到登录循环</h3>
            <p>系统检测到login.html和tasks.html之间存在无限跳转循环。</p>
            <p>可能的原因:</p>
            <ul style="text-align:left">
                <li>您的登录状态不一致或Token无效</li>
                <li>后端API认证失败但前端保留了无效Token</li>
                <li>浏览器缓存导致页面状态混乱</li>
            </ul>
            <div style="margin-top:15px">
                <button id="auth-reset-btn" class="btn btn-danger" style="margin-right:10px">
                    重置登录状态
                </button>
                <button id="auth-continue-btn" class="btn btn-secondary">
                    继续访问当前页面
                </button>
            </div>
        `;
        
        // 添加到页面
        document.body.appendChild(errorDiv);
        
        // 绑定重置按钮事件
        document.getElementById('auth-reset-btn').addEventListener('click', function() {
            // 清除所有登录状态和循环检测状态
            localStorage.removeItem('jwtToken');
            localStorage.removeItem('username');
            sessionStorage.removeItem('lastAuthRedirect');
            sessionStorage.removeItem('lastAuthPage');
            sessionStorage.removeItem('authLoopCount');
            sessionStorage.removeItem('authSafeMode');
            
            // 在清除状态后，强制重载当前页面
            window.location.reload(true);
        });
        
        // 绑定继续按钮事件
        document.getElementById('auth-continue-btn').addEventListener('click', function() {
            // 隐藏错误提示，允许用户在当前页面继续操作
            document.getElementById('auth-loop-error').style.display = 'none';
            sessionStorage.setItem('authLoopIgnore', 'true');
        });
    }
    
    // 如果在安全模式下，立即监听各种跳转方法
    if (sessionStorage.getItem('authSafeMode') === 'true' && !sessionStorage.getItem('authLoopIgnore')) {
        console.warn('认证安全模式已启用，将阻止login.html和tasks.html之间的跳转');
        
        // 如果页面已加载完成，显示错误
        if (document.readyState === 'complete' || document.readyState === 'interactive') {
            showAuthLoopError();
        } else {
            // 否则等待页面加载完成
            window.addEventListener('DOMContentLoaded', showAuthLoopError);
        }
    }
})();
