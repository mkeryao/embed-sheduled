/**
 * workflow-page-patch.js
 * 页面级补丁，用于修复工作流关键函数并防止栈溢出
 */

// 使用IIFE确保变量不会泄漏到全局作用域
(function() {
    console.log('工作流页面补丁已加载，准备应用...');
    
    // 等待DOM加载
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
    
    // 初始化补丁
    function init() {
        // 等待页面完全加载（包括所有其他脚本）
        window.addEventListener('load', function() {
            try {
                console.log('应用工作流页面补丁...');
                
                // 应用安全的safeFormatWorkflowJSON函数
                applySafeWorkflowJSONFormatter();
                
                // 修复重复定义的函数
                fixDuplicateFunctions();
                
                // 添加工作流处理的防御措施
                addWorkflowDefensiveMeasures();
                
                console.log('工作流页面补丁应用完成');
            } catch (error) {
                console.error('应用工作流页面补丁时发生错误:', error);
            }
        });
    }
    
    /**
     * 确保工作流依赖已正确加载
     * @returns {boolean} 依赖是否全部加载
     */
    function ensureWorkflowDependencies() {
        const dependencies = [
            { name: 'jQuery', check: () => typeof jQuery !== 'undefined' },
            { name: 'cytoscape', check: () => typeof cytoscape !== 'undefined' },
            { name: 'dagre', check: () => typeof dagre !== 'undefined' },
            { name: 'WorkflowDagViewer', check: () => typeof WorkflowDagViewer !== 'undefined' },
            { name: 'WorkflowInitializer', check: () => typeof WorkflowInitializer !== 'undefined' }
        ];
        
        const missing = dependencies.filter(dep => !dep.check());
        
        if (missing.length > 0) {
            console.warn(`缺少工作流依赖: ${missing.map(d => d.name).join(', ')}`);
            return false;
        }
        
        return true;
    }

    /**
     * 初始化或重新加载工作流DAG图
     */
    function initializeOrRefreshWorkflowDAG() {
        console.log('尝试初始化或刷新工作流DAG图...');
        
        if (!ensureWorkflowDependencies()) {
            console.error('工作流依赖未完全加载，无法初始化DAG图');
            const container = document.getElementById('workflow-dag-content');
            if (container) {
                container.innerHTML = `
                    <div class="alert alert-warning mt-3">
                        <i class="fas fa-exclamation-triangle"></i> 
                        工作流可视化组件未完全加载，请刷新页面重试。
                    </div>`;
            }
            return false;
        }
        
        // 调用工作流初始化器的刷新函数
        if (typeof WorkflowInitializer !== 'undefined' && 
            typeof WorkflowInitializer.initOrRefreshWorkflowDAG === 'function') {
            console.log('调用WorkflowInitializer.initOrRefreshWorkflowDAG()');
            WorkflowInitializer.initOrRefreshWorkflowDAG();
            return true;
        } else {
            console.error('WorkflowInitializer.initOrRefreshWorkflowDAG不可用');
            return false;
        }
    }
    
    /**
     * 应用安全的工作流JSON格式化函数
     */
    function applySafeWorkflowJSONFormatter() {
        try {
            // 创建更安全的格式化函数，简单直接
            window.safeFormatWorkflowJSON = function(input, type) {
                try {
                    // 如果输入为空，返回默认值
                    if (!input) {
                        return type === 'params' ? {} : [];
                    }
                    
                    // 如果输入已经是对象，直接返回
                    if (typeof input === 'object' && input !== null) {
                        return input;
                    }
                    
                    // 尝试简单解析
                    if (typeof input === 'string') {
                        if (input.trim() === '') {
                            return type === 'params' ? {} : [];
                        }
                        
                        try {
                            return JSON.parse(input);
                        } catch (e) {
                            console.warn('JSON格式错误，返回安全值:', e.message);
                            return type === 'params' ? {} : [];
                        }
                    }
                    
                    // 默认返回安全值
                    return type === 'params' ? {} : [];
                } catch (e) {
                    console.error('JSON处理异常:', e.message);
                    return type === 'params' ? {} : [];
                }
            };
            
            console.log('安全的工作流JSON格式化函数已应用');
        } catch (e) {
            console.error('应用安全的工作流JSON格式化函数时发生错误:', e);
        }
    }
    
    /**
     * 修复重复定义的函数
     */
    function fixDuplicateFunctions() {
        try {
            // 检查是否已存在工作流节点边处理器
            if (window.WorkflowNodeEdgeHandler) {
                // 使用处理器中定义的函数
                window.addWorkflowNodeItem = window.WorkflowNodeEdgeHandler.addWorkflowNodeItem;
                window.addWorkflowEdgeItem = window.WorkflowNodeEdgeHandler.addWorkflowEdgeItem;
                window.updateNodeSelectors = window.WorkflowNodeEdgeHandler.updateNodeSelectors;
                window.updateEdgeNodeSelectors = window.WorkflowNodeEdgeHandler.updateEdgeNodeSelectors;
                
                // 添加或修复工作流DAG图更新函数
                window.updateWorkflowDag = function() {
                    console.log('调用updateWorkflowDag函数更新工作流DAG图...');
                    // 尝试使用WorkflowInitializer
                    if (typeof WorkflowInitializer !== 'undefined' && 
                        typeof WorkflowInitializer.initOrRefreshWorkflowDAG === 'function') {
                        WorkflowInitializer.initOrRefreshWorkflowDAG();
                    } 
                    // 备用方法：如果存在全局实例则直接使用
                    else if (window.workflowDagEditor && 
                            typeof window.workflowDagEditor.updateData === 'function') {
                        try {
                            // 从表单数据中读取节点和边
                            const nodesJson = $('#workflowNodesJson').val();
                            const edgesJson = $('#workflowEdgesJson').val();
                            
                            let nodes = [], edges = [];
                            try {
                                if (nodesJson) nodes = JSON.parse(nodesJson);
                                if (edgesJson) edges = JSON.parse(edgesJson);
                            } catch (e) {
                                console.error('解析工作流JSON出错:', e);
                            }
                            
                            console.log(`直接更新DAG数据: ${nodes.length}个节点, ${edges.length}个边`);
                            window.workflowDagEditor.updateData(nodes, edges);
                        } catch (e) {
                            console.error('更新DAG数据时出错:', e);
                        }
                    }
                };
                window.updateWorkflowNodesJson = window.WorkflowNodeEdgeHandler.updateWorkflowNodesJson;
                window.updateWorkflowEdgesJson = window.WorkflowNodeEdgeHandler.updateWorkflowEdgesJson;
                
                console.log('已从处理器导入工作流函数');
            } else {
                console.warn('工作流节点边处理器未找到，某些功能可能不可用');
            }
        } catch (e) {
            console.error('修复重复定义的函数时发生错误:', e);
        }
    }
    
    /**
     * 添加工作流处理的防御措施
     */
    function addWorkflowDefensiveMeasures() {
        try {
            // 为工作流DAG标签页添加点击事件处理
            $('#workflow-dag-tab').off('click.workflowInit').on('click.workflowInit', function(e) {
                console.log('工作流DAG标签页被点击，准备初始化/刷新DAG图...');
                // 延迟执行，确保标签页已切换
                setTimeout(function() {
                    initializeOrRefreshWorkflowDAG();
                }, 300);
            });
            
            // 为任务类型选择添加工作流相关处理
            $('#taskTypeSelect').on('change', function() {
                const selectedType = $(this).val();
                if (selectedType === '10') { // 工作流类型
                    console.log('已选择工作流类型任务，准备初始化工作流编辑器...');
                }
            });
            
            // 防止updateWorkflowDag递归调用导致栈溢出
            if (typeof window.updateWorkflowDag === 'function') {
                const originalUpdateWorkflowDag = window.updateWorkflowDag;
                
                // 重写updateWorkflowDag函数
                window.updateWorkflowDag = function() {
                    // 防止递归调用
                    if (!window._workflowUpdateState) {
                        window._workflowUpdateState = {
                            depth: 0,
                            lastUpdateTime: 0,
                            updateCount: 0
                        };
                    }
                    
                    const now = Date.now();
                    const state = window._workflowUpdateState;
                    
                    // 重置计数器（如果超过5秒没有更新）
                    if (now - state.lastUpdateTime > 5000) {
                        state.updateCount = 0;
                    }
                    
                    // 增加更新计数
                    state.updateCount++;
                    state.lastUpdateTime = now;
                    
                    // 防止短时间内过多更新
                    if (state.updateCount > 10) {
                        console.warn('检测到过多工作流更新，暂停更新');
                        setTimeout(() => { state.updateCount = 0; }, 3000);
                        return false;
                    }
                    
                    // 防止递归调用过深
                    state.depth++;
                    if (state.depth > 3) {
                        console.error('检测到工作流更新递归过深，中断递归');
                        state.depth = 0;
                        return false;
                    }
                    
                    try {
                        // 调用原始函数
                        return originalUpdateWorkflowDag.apply(this, arguments);
                    } finally {
                        // 减少递归深度
                        state.depth--;
                    }
                };
                
                console.log('已添加工作流更新防递归保护');
            }
            
            // 添加JSON变更监控
            addJsonChangeMonitors();
            
            console.log('已添加工作流处理的防御措施');
        } catch (e) {
            console.error('添加工作流处理的防御措施时发生错误:', e);
        }
    }
    
    // 监控工作流JSON区域变更，使用节流函数防止频繁更新
    function addJsonChangeMonitors() {
        if (typeof jQuery !== 'undefined') {
            const $workflowNodes = $('#workflowNodesJson');
            const $workflowEdges = $('#workflowEdgesJson');
            
            if ($workflowNodes.length && $workflowEdges.length) {
                // 创建节流函数
                function throttle(func, delay) {
                    let lastCall = 0;
                    return function() {
                        const now = Date.now();
                        if (now - lastCall >= delay) {
                            lastCall = now;
                            return func.apply(this, arguments);
                        }
                    };
                }
                
                // 安全的更新工作流DAG
                const safeUpdateWorkflowDag = throttle(function() {
                    if (typeof window.updateWorkflowDag === 'function') {
                        console.log('节流后执行工作流更新');
                        window.updateWorkflowDag();
                    }
                }, 1000); // 1秒内最多执行一次
                
                // 更新原始的change事件处理
                $workflowNodes.off('change').on('change', safeUpdateWorkflowDag);
                $workflowEdges.off('change').on('change', safeUpdateWorkflowDag);
                
                console.log('工作流JSON区域变更事件已优化');
            }
        }
    }
    
    // 导出页面补丁公共API
    window.WorkflowPagePatch = {
        ensureWorkflowDependencies: ensureWorkflowDependencies,
        initializeOrRefreshWorkflowDAG: initializeOrRefreshWorkflowDAG,
        triggerDagInitialization: function() {
            setTimeout(initializeOrRefreshWorkflowDAG, 300);
        }
    };
})();
