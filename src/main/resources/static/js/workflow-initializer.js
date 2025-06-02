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
                // 尝试修复tasks.html中可能的依赖问题
                console.log('检测到tasks.html页面，尝试加载核心依赖');
                
                // 确保jQuery加载
                if (typeof jQuery === 'undefined') {
                    console.log('动态加载jQuery');
                    const script = document.createElement('script');
                    script.src = 'libs/jquery/jquery-3.5.1.min.js';
                    script.onload = function() {
                        console.log('jQuery已加载，重新初始化');
                        initialize();
                    };
                    document.head.appendChild(script);
                    return;
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
                        nodes = typeof window.formatWorkflowJSON === 'function' 
                            ? window.formatWorkflowJSON(nodesJson, 'nodes') 
                            : JSON.parse(nodesJson);
                    }
                    
                    if (edgesJson) {
                        edges = typeof window.formatWorkflowJSON === 'function'
                            ? window.formatWorkflowJSON(edgesJson, 'edges')
                            : JSON.parse(edgesJson);
                    }
                } catch (e) {
                    console.error('解析工作流JSON数据失败:', e);
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
