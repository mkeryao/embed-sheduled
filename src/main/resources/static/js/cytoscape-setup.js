/**
 * 确保Cytoscape及其dagre布局插件正确加载和注册
 * 解决工作流可视化问题
 */

(function() {
    console.log('初始化Cytoscape设置...');

    // 确保Cytoscape已加载
    function ensureCytoscape(callback) {
        if (typeof cytoscape !== 'undefined') {
            console.log('Cytoscape已加载');
            callback();
        } else {
            console.warn('Cytoscape未加载，正在动态加载...');
            
            const script = document.createElement('script');
            script.src = 'libs/cytoscape/cytoscape.min.js';
            script.onload = function() {
                console.log('Cytoscape已成功加载');
                callback();
            };
            script.onerror = function() {
                console.error('加载Cytoscape失败');
                alert('无法加载工作流可视化库，请刷新页面重试');
            };
            document.head.appendChild(script);
        }
    }

    // 确保dagre插件已加载和注册
    function ensureDagrePlugin() {
        if (typeof cytoscape === 'undefined') {
            console.error('Cytoscape未加载，无法注册dagre插件');
            return false;
        }
        
        // 检查dagre布局是否已存在
        if (typeof cytoscape.layouts !== 'undefined' && typeof cytoscape.layouts.dagre !== 'undefined') {
            console.log('dagre布局插件已加载');
            return true;
        }
        
        // 尝试加载dagre插件
        return loadDagrePlugin();
    }

    // 加载dagre插件
    function loadDagrePlugin() {
        return new Promise(function(resolve, reject) {
            console.log('正在加载dagre布局插件...');
            
            try {
                // 检查全局dagre对象是否存在
                const script = document.createElement('script');
                script.src = 'libs/cytoscape/layout/cytoscape-dagre.js';
                
                script.onload = function() {
                    // 检查插件是否已成功注册
                    setTimeout(function() {
                        if (typeof cytoscape !== 'undefined' && 
                           typeof cytoscape.layouts !== 'undefined' && 
                           typeof cytoscape.layouts.dagre !== 'undefined') {
                            console.log('dagre布局插件已成功加载和注册');
                            resolve(true);
                        } else {
                            console.warn('dagre布局插件已加载但未正确注册，尝试手动注册');
                            
                            // 尝试手动注册
                            try {
                                // 如果存在全局dagre对象，尝试手动注册
                                if (typeof dagre !== 'undefined' && typeof cytoscape.use === 'function') {
                                    cytoscape.use(dagre);
                                    console.log('已手动注册dagre布局插件');
                                    resolve(true);
                                } else {
                                    // 创建备用的dagre布局实现
                                    registerFallbackDagre();
                                    console.log('已注册备用dagre布局');
                                    resolve(true);
                                }
                            } catch(e) {
                                console.error('注册dagre布局插件失败:', e);
                                registerFallbackDagre();
                            resolve(false);
                        }
                    }
                }, 300);
            };
            
                script.onerror = function() {
                    console.error('加载dagre布局插件失败');
                    // 注册备用布局
                    registerFallbackDagre();
                    resolve(false);
                };
                
                document.head.appendChild(script);
            } catch(e) {
                console.error('加载dagre插件过程中发生错误:', e);
                registerFallbackDagre();
                resolve(false);
            }
        });
    }

    // 注册备用的dagre布局
    function registerFallbackDagre() {
        try {
            if (typeof cytoscape !== 'undefined') {
                // 创建一个基于内置breadthfirst布局的替代dagre布局
                cytoscape('layout', 'dagre', function(opts) {
                    var options = Object.assign({
                        name: 'breadthfirst',
                        directed: true,
                        fit: true,
                        padding: 30,
                        spacingFactor: 1.5,
                        nodeDimensionsIncludeLabels: true,
                        animate: opts.animate || false
                    }, opts);
                    
                    // 删除dagre特有的选项以避免错误
                    delete options.rankDir;
                    delete options.ranker;
                    delete options.edgeSep;
                    delete options.rankSep;
                    delete options.edgeWeight;
                    
                    console.log('使用备用breadthfirst布局替代dagre');
                    return this.layout(options);
                });
                console.log('已注册备用dagre布局');
                window._hasFallbackDagre = true;
                return true;
            }
        } catch(e) {
            console.error('注册备用dagre布局失败:', e);
            return false;
        }
        return false;
    }

    // 初始化函数
    function initCytoscapeSetup() {
        ensureCytoscape(function() {
            ensureDagrePlugin().then(function(success) {
                if (success) {
                    console.log('Cytoscape及其dagre布局已成功初始化');
                    // 触发一个事件，通知其他脚本Cytoscape已准备就绪
                    document.dispatchEvent(new CustomEvent('cytoscapeReady'));
                } else {
                    console.warn('使用备用dagre布局初始化完成');
                    document.dispatchEvent(new CustomEvent('cytoscapeReady', {detail: {useFallback: true}}));
                }
            });
        });
    }

    // 在页面加载完成后初始化
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        initCytoscapeSetup();
    } else {
        window.addEventListener('DOMContentLoaded', initCytoscapeSetup);
    }
})();
