/**
 * 工作流初始化器
 * 确保工作流组件在所有依赖加载完成后初始化
 */

(function() {
    // 检查jQuery是否可用的函数
    function ensureDependencies(callback) {
        console.log('检查工作流依赖...');
        
        // 检查jQuery
        if (typeof jQuery === 'undefined') {
            console.error('错误: jQuery未加载!');
            showNoDependencyError('jQuery');
            return false;
        }
        
        // 检查Cytoscape
        if (typeof cytoscape === 'undefined') {
            console.error('错误: Cytoscape未加载!');
            showNoDependencyError('Cytoscape');
            return false;
        }
        
        // 检查WorkflowDagViewer
        if (typeof WorkflowDagViewer === 'undefined') {
            console.error('错误: WorkflowDagViewer未加载!');
            showNoDependencyError('WorkflowDagViewer');
            return false;
        }
        
        // 所有依赖都已加载
        console.log('所有工作流依赖已加载');
        if (typeof callback === 'function') {
            callback();
        }
        return true;
    }
    
    // 显示依赖错误消息
    function showNoDependencyError(name) {
        // 防御性检查，即使jQuery未加载也能工作
        const containers = document.querySelectorAll('.workflow-dag-container, #workflow-dag-content, #workflow-dag-view');
        containers.forEach(container => {
            container.innerHTML = `
                <div style="padding: 15px; background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; border-radius: 4px; margin: 10px 0;">
                    <h5 style="margin-top: 0;">加载错误</h5>
                    <p>无法加载工作流组件: ${name} 未加载。</p>
                    <p>请刷新页面或检查网络连接。</p>
                </div>
            `;
        });
    }
    
    // 当DOM加载完成后执行
    function initialize() {
        console.log('工作流初始化器启动');
        
        // 确保所有依赖可用
        if (!ensureDependencies()) {
            // 对于tasks.html页面的特殊处理
            if (window.location.pathname.indexOf('tasks.html') !== -1) {
                console.log('检测到tasks.html页面，尝试安全恢复而非刷新页面');
                
                // 检查是否处于安全模式
                if (sessionStorage.getItem('safeMode') === 'true') {
                    console.warn('页面处于安全模式，不尝试自动加载依赖');
                    
                    // 显示错误信息但不刷新
                    const containers = document.querySelectorAll('.workflow-dag-container, #workflow-dag-content, #workflow-dag-view');
                    containers.forEach(container => {
                        container.innerHTML = `
                            <div class="alert alert-warning">
                                <i class="fas fa-exclamation-triangle"></i> 
                                工作流组件依赖缺失。请确保所有JS库已正确加载，然后重试。
                                <button class="btn btn-sm btn-outline-primary load-workflow-deps-btn">
                                    尝试加载依赖
                                </button>
                            </div>
                        `;
                    });
                    
                    // 绑定按钮事件以手动加载依赖
                    setTimeout(() => {
                        document.querySelectorAll('.load-workflow-deps-btn').forEach(btn => {
                            btn.addEventListener('click', function() {
                                loadWorkflowDependencies();
                            });
                        });
                    }, 500);
                    
                    return;
                }
                
                // 尝试修复tasks.html中可能的依赖问题，但不刷新页面
                console.log('检测到tasks.html页面，尝试手动加载核心依赖');
                loadWorkflowDependencies();
            }
            
            // 加载工作流依赖的函数
            function loadWorkflowDependencies() {
                // 检查jQuery
                if (typeof jQuery === 'undefined') {
                    console.log('动态加载jQuery');
                    loadScript('libs/jquery/jquery-3.5.1.min.js', function() {
                        loadCytoscape();
                    });
                } else {
                    loadCytoscape();
                }
                
                // 加载Cytoscape
                function loadCytoscape() {
                    if (typeof cytoscape === 'undefined') {
                        console.log('动态加载Cytoscape');
                        loadScript('libs/cytoscape/cytoscape.min.js', function() {
                            loadDagre();
                        });
                    } else {
                        loadDagre();
                    }
                }
                
                // 加载Dagre
                function loadDagre() {
                    if (typeof dagre === 'undefined') {
                        console.log('动态加载Dagre');
                        loadScript('libs/cytoscape/dagre.min.js', function() {
                            loadCytoscapeDagre();
                        });
                    } else {
                        loadCytoscapeDagre();
                    }
                }
                
                // 加载Cytoscape-Dagre
                function loadCytoscapeDagre() {
                    if (typeof cytoscape.layouts.dagre === 'undefined') {
                        console.log('动态加载Cytoscape-Dagre');
                        loadScript('libs/cytoscape/layout/cytoscape-dagre.js', function() {
                            console.log('所有依赖加载完成，尝试初始化工作流...');
                            
                            // 显示依赖已加载，但不刷新页面
                            jQuery('.workflow-dag-container, #workflow-dag-content, #workflow-dag-view').each(function() {
                                jQuery(this).html(`
                                    <div class="alert alert-success">
                                        <i class="fas fa-check-circle"></i> 
                                        工作流依赖已加载完成，可以使用DAG图功能。
                                        <button class="btn btn-sm btn-primary refresh-dag-btn">
                                            刷新DAG图
                                        </button>
                                    </div>
                                `);
                            });
                            
                            // 绑定按钮事件
                            jQuery('.refresh-dag-btn').on('click', function() {
                                if (typeof WorkflowInitializer !== 'undefined' && 
                                    typeof WorkflowInitializer.initOrRefreshWorkflowDAG === 'function') {
                                    WorkflowInitializer.initOrRefreshWorkflowDAG();
                                }
                            });
                        });
                    }
                }
                
                // 通用脚本加载函数
                function loadScript(url, callback) {
                    const script = document.createElement('script');
                    script.type = 'text/javascript';
                    script.src = url;
                    script.onload = callback;
                    script.onerror = function() {
                        console.error(`加载脚本失败: ${url}`);
                    };
                    document.head.appendChild(script);
                }
            }
        }
        
        // 对工作流相关DOM元素添加初始化逻辑
        // 在页面DOM加载完毕后，延迟执行工作流初始化
        if (document.readyState === 'complete' || document.readyState === 'interactive') {
            setTimeout(initWorkflowElements, 300);
        } else {
            document.addEventListener('DOMContentLoaded', function() {
                setTimeout(initWorkflowElements, 300);
            });
        }
    }
    
    // 初始化页面上的工作流元素
    function initWorkflowElements() {
        // 如果jQuery可用，使用它来查找和初始化工作流元素
        if (typeof jQuery !== 'undefined') {
            // 对于工作流创建/编辑标签页
            jQuery('#workflow-dag-tab').on('click', function() {
                console.log('工作流DAG标签被点击，确保DAG视图初始化');
                setTimeout(function() {
                    // 初始化或刷新DAG图
                    initOrRefreshWorkflowDAG();
                }, 200);
            });
            
            // 工作流模态框打开时
            jQuery('#taskFormModal').on('shown.bs.modal', function() {
                const taskType = jQuery('#taskType').val();
                if (taskType === '10') { // 工作流类型
                    console.log('工作流任务模态框打开，准备DAG视图');
                    // 确保节点和边数据是最新的
                    updateWorkflowJsonFromUI();
                }
            });
        }
        
        console.log('工作流元素初始化完成');
    }
    
    // 初始化或刷新工作流DAG图
    function initOrRefreshWorkflowDAG() {
        try {
            console.log('初始化或刷新工作流DAG图');
            
            // 确保依赖已加载
            if (!ensureDependencies()) {
                return;
            }
            
            // 查找DAG容器
            const dagContainer = document.getElementById('workflow-dag-content');
            if (!dagContainer) {
                console.warn('未找到工作流DAG容器');
                return;
            }
            
            // 查找或创建DAG视图容器
            let dagViewContainer = document.getElementById('workflow-dag-editor');
            if (!dagViewContainer) {
                console.log('创建工作流DAG编辑器容器');
                dagViewContainer = document.createElement('div');
                dagViewContainer.id = 'workflow-dag-editor';
                dagViewContainer.className = 'workflow-dag-container';
                dagViewContainer.style.minHeight = '400px';
                dagContainer.appendChild(dagViewContainer);
            }
            
            // 读取工作流数据
            let nodes = [];
            let edges = [];
            
            if (typeof jQuery !== 'undefined') {
                // 尝试从表单读取数据
                const nodesJson = jQuery('#workflowNodesJson').val();
                const edgesJson = jQuery('#workflowEdgesJson').val();
                
                try {
                    if (nodesJson) {
                        // 优先使用安全包装函数
                        if (typeof window.safeFormatWorkflowJSON === 'function') {
                            nodes = window.safeFormatWorkflowJSON(nodesJson, 'nodes', {
                                enableSizeRestriction: true,
                                maxInputLength: 500000 // 更严格的限制
                            });
                        } else if (typeof window.formatWorkflowJSON === 'function') {
                            nodes = window.formatWorkflowJSON(nodesJson, 'nodes');
                        } else {
                            // 简单的解析尝试
                            try {
                                nodes = JSON.parse(nodesJson);
                            } catch (parseError) {
                                console.warn('节点JSON解析失败，使用空数组:', parseError);
                                nodes = [];
                            }
                        }
                    }
                    
                    if (edgesJson) {
                        // 优先使用安全包装函数
                        if (typeof window.safeFormatWorkflowJSON === 'function') {
                            edges = window.safeFormatWorkflowJSON(edgesJson, 'edges', {
                                enableSizeRestriction: true,
                                maxInputLength: 500000 // 更严格的限制
                            });
                        } else if (typeof window.formatWorkflowJSON === 'function') {
                            edges = window.formatWorkflowJSON(edgesJson, 'edges');
                        } else {
                            // 简单的解析尝试
                            try {
                                edges = JSON.parse(edgesJson);
                            } catch (parseError) {
                                console.warn('边JSON解析失败，使用空数组:', parseError);
                                edges = [];
                            }
                        }
                    }
                    
                    // 确保结果是数组
                    if (!Array.isArray(nodes)) nodes = [];
                    if (!Array.isArray(edges)) edges = [];
                    
                } catch (e) {
                    console.error('解析工作流JSON数据失败:', e);
                    nodes = [];
                    edges = [];
                }
            }
            
            // 初始化DAG查看器
            if (typeof WorkflowDagViewer !== 'undefined') {
                if (window.workflowDagEditor) {
                    // 如果已存在实例，更新数据
                    console.log('更新现有DAG编辑器');
                    window.workflowDagEditor.updateData(nodes, edges);
                } else {
                    // 创建新实例
                    console.log('创建新的DAG编辑器');
                    window.workflowDagEditor = new WorkflowDagViewer('workflow-dag-editor', {
                        readOnly: false,
                        onChange: function(data) {
                            // 更新表单中的JSON
                            if (typeof jQuery !== 'undefined') {
                                jQuery('#workflowNodesJson').val(JSON.stringify(data.nodes, null, 2));
                                jQuery('#workflowEdgesJson').val(JSON.stringify(data.edges, null, 2));
                            }
                        }
                    });
                    window.workflowDagEditor.updateData(nodes, edges);
                }
            } else {
                console.error('WorkflowDagViewer 不可用');
                if (dagViewContainer) {
                    dagViewContainer.innerHTML = '<div class="alert alert-danger">无法加载工作流图组件</div>';
                }
            }
        } catch (error) {
            console.error('初始化工作流DAG图时出错:', error);
        }
    }
    
    // 从UI更新工作流JSON
    function updateWorkflowJsonFromUI() {
        if (typeof jQuery === 'undefined') return;
        
        try {
            // 收集所有工作流节点数据
            const nodes = [];
            jQuery('.workflow-node-item').each(function() {
                const $node = jQuery(this);
                const nodeId = $node.find('.node-id').val() || $node.data('node-id');
                const nodeName = $node.find('.node-name').val();
                const taskConfigId = $node.find('.task-config-id').val();
                
                if (nodeId) {
                    nodes.push({
                        nodeId: nodeId,
                        nodeName: nodeName || `节点 ${nodeId}`,
                        taskConfigId: taskConfigId ? parseInt(taskConfigId, 10) : null
                    });
                }
            });
            
            // 收集所有工作流边数据
            const edges = [];
            jQuery('.workflow-edge-item').each(function() {
                const $edge = jQuery(this);
                const fromNodeId = $edge.find('.from-node-id').val();
                const toNodeId = $edge.find('.to-node-id').val();
                
                if (fromNodeId && toNodeId) {
                    edges.push({
                        fromNodeId: fromNodeId,
                        toNodeId: toNodeId,
                        priority: 1
                    });
                }
            });
            
            // 更新表单隐藏字段
            jQuery('#workflowNodesJson').val(JSON.stringify(nodes, null, 2));
            jQuery('#workflowEdgesJson').val(JSON.stringify(edges, null, 2));
            
            console.log('工作流JSON已从UI更新');
            return { nodes, edges };
        } catch (e) {
            console.error('更新工作流JSON时出错:', e);
            return null;
        }
    }
    
    // 启动初始化
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
    
    // 对外暴露API
    window.WorkflowInitializer = {
        ensureDependencies: ensureDependencies,
        initOrRefreshWorkflowDAG: initOrRefreshWorkflowDAG,
        updateWorkflowJsonFromUI: updateWorkflowJsonFromUI
    };
})();
