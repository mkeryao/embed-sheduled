/**
 * workflow-component-loader.js
 * 用于安全加载工作流所需的所有组件
 */

(function() {
    console.log('工作流组件加载器初始化');
    
    // 在DOM加载完成后执行
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
    
    // 初始化函数
    function init() {
        console.log('工作流组件加载器开始检查依赖');
        
        // 创建依赖状态管理
        window.workflowDependencies = {
            'jquery': { loaded: typeof jQuery !== 'undefined', path: 'libs/jquery/jquery-3.5.1.min.js' },
            'cytoscape': { loaded: typeof cytoscape !== 'undefined', path: 'libs/cytoscape/cytoscape.min.js' },
            'dagre': { loaded: typeof dagre !== 'undefined', path: 'libs/cytoscape/dagre.min.js' },
            'cytoscape-dagre': { loaded: typeof cytoscape !== 'undefined' && typeof cytoscape.layouts !== 'undefined' && typeof cytoscape.layouts.dagre !== 'undefined', path: 'libs/cytoscape/layout/cytoscape-dagre.js' }
        };
        
        // 诊断日志
        const missingDeps = [];
        Object.keys(window.workflowDependencies).forEach(dep => {
            if (!window.workflowDependencies[dep].loaded) {
                missingDeps.push(dep);
            }
        });
        
        if (missingDeps.length > 0) {
            console.warn(`缺少工作流依赖: ${missingDeps.join(', ')}`);
        } else {
            console.log('所有工作流依赖已正确加载');
        }
        
        // 向window对象添加工作流依赖加载函数
        window.ensureWorkflowDependencies = function(callback) {
            loadDependenciesInOrder(['jquery', 'cytoscape', 'dagre', 'cytoscape-dagre'], callback);
        };
        
        // 顺序加载依赖
        function loadDependenciesInOrder(dependencies, finalCallback, index = 0) {
            if (index >= dependencies.length) {
                if (typeof finalCallback === 'function') {
                    finalCallback();
                }
                return;
            }
            
            const depName = dependencies[index];
            const dep = window.workflowDependencies[depName];
            
            if (dep.loaded) {
                // 如果依赖已加载，继续下一个
                loadDependenciesInOrder(dependencies, finalCallback, index + 1);
            } else {
                // 加载依赖
                console.log(`正在加载依赖: ${depName}`);
                const script = document.createElement('script');
                script.src = dep.path;
                
                script.onload = function() {
                    console.log(`依赖加载成功: ${depName}`);
                    dep.loaded = true;
                    
                    // 继续加载下一个依赖
                    loadDependenciesInOrder(dependencies, finalCallback, index + 1);
                };
                
                script.onerror = function(e) {
                    console.error(`依赖加载失败: ${depName}`, e);
                    dep.error = true;
                    
                    // 尽管出错，仍然尝试继续加载下一个依赖
                    loadDependenciesInOrder(dependencies, finalCallback, index + 1);
                };
                
                document.head.appendChild(script);
            }
        }
    }
})();
