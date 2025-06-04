/**
 * workflow-node-edge-handler.js
 * 工作流节点和边的处理函数
 * 解决在tasks.html中重复定义的函数问题
 */

// 确保在DOM加载完毕后执行
document.addEventListener('DOMContentLoaded', function() {
    console.log('工作流节点和边处理器初始化...');
});

/**
 * 添加工作流节点项到UI
 * @param {Object} node - 节点数据对象
 * @returns {jQuery} 添加的节点DOM元素
 */
function addWorkflowNodeItem(node) {
    if (!node) {
        console.error('尝试添加无效节点：', node);
        return null;
    }
    
    try {
        // 确保节点至少有ID属性
        if (!node.nodeId) {
            node.nodeId = 'node_' + new Date().getTime() + '_' + Math.floor(Math.random() * 1000);
            console.warn('节点缺少ID，已自动生成: ' + node.nodeId);
        }
        
        const container = $('.workflow-nodes-container');
        if (container.length === 0) {
            console.error('找不到工作流节点容器');
            return null;
        }

        // 检查是否已存在此节点ID
        if (container.find(`[data-node-id="${node.nodeId}"]`).length > 0) {
            console.warn(`节点ID "${node.nodeId}" 已存在，跳过重复添加`);
            return container.find(`[data-node-id="${node.nodeId}"]`);
        }

        const nodeItem = $(`<div class="workflow-node-item" data-node-id="${node.nodeId}">
            <div class="form-row">
                <div class="form-group col-md-6">
                    <label>节点ID</label>
                    <input type="text" class="form-control form-control-sm node-id-input" value="${node.nodeId}" readonly>
                </div>
                <div class="form-group col-md-6">
                    <label>任务</label>
                    <select class="form-control form-control-sm node-task-select">
                        <option value="">-- 选择任务 --</option>
                    </select>
                </div>
            </div>
            <div class="form-row">
                <div class="form-group col-md-9">
                    <label>节点名称</label>
                    <input type="text" class="form-control form-control-sm node-name-input" value="${node.nodeName || ''}">
                </div>
                <div class="form-group col-md-3">
                    <label>&nbsp;</label>
                    <button type="button" class="btn btn-sm btn-danger form-control delete-node-btn">删除</button>
                </div>
            </div>
        </div>`);

        container.append(nodeItem);

        // 绑定删除节点按钮事件
        nodeItem.find('.delete-node-btn').on('click', function() {
            nodeItem.remove();
            // 更新节点JSON和DAG图
            updateWorkflowNodesJson();
            updateNodeSelectors();
            
            // 如果当前显示的是DAG标签页，更新工作流DAG图
            if ($('#workflow-dag-content').hasClass('active') && typeof updateWorkflowDag === 'function') {
                updateWorkflowDag();
            }
        });

        // 绑定输入变更事件
        nodeItem.find('input, select').on('change', function() {
            updateWorkflowNodesJson();

            // 如果更改的是节点ID或者节点名称，更新边选择器
            if ($(this).hasClass('node-id-input') || $(this).hasClass('node-name-input')) {
                updateNodeSelectors();
            }
        });

        // 填充任务选择器（使用formData.beanTasks）
        if (window.formData && window.formData.beanTasks && window.formData.beanTasks.length > 0) {
            const taskSelect = nodeItem.find('.node-task-select');
            const currentTaskId = node.taskConfigId ? parseInt(node.taskConfigId) : null;
            
            window.formData.beanTasks.forEach(function(task) {
                // 兼容处理任务对象格式差异（不同API可能返回不同格式的对象）
                const taskId = task.task_id || task.taskId;
                const taskName = task.task_name || task.taskName;
                const beanName = task.bean_name || task.beanName;
                
                // 检查是否为当前选中的任务
                const selected = currentTaskId && taskId == currentTaskId ? 'selected' : '';
                
                // 添加选项到下拉框
                taskSelect.append(`<option value="${taskId}" ${selected}>${taskName} (${beanName})</option>`);
            });
            console.log(`已加载${window.formData.beanTasks.length}个任务到选择器`);
        } else {
            console.warn('无法加载任务列表到选择器，formData.beanTasks不可用或为空');
            
            // 如果有taskConfigId，至少显示一个任务ID占位符
            if (node.taskConfigId) {
                const taskSelect = nodeItem.find('.node-task-select');
                taskSelect.append(`<option value="${node.taskConfigId}" selected>任务 #${node.taskConfigId}</option>`);
                console.log(`添加了任务ID ${node.taskConfigId} 作为占位符`);
            }
        }

        // 如有必要，更新所有边的节点选择器
        if (typeof updateNodeSelectors === 'function') {
            updateNodeSelectors();
        }

        return nodeItem;
    } catch (e) {
        console.error('添加工作流节点时发生错误:', e);
        return null;
    }
}

/**
 * 添加工作流边项到UI
 * @param {Object} edge - 边数据对象
 * @returns {jQuery} 添加的边DOM元素
 */
function addWorkflowEdgeItem(edge) {
    if (!edge) {
        console.error('尝试添加无效边：', edge);
        return null;
    }
    
    try {
        const container = $('.workflow-edges-container');
        if (container.length === 0) {
            console.error('找不到工作流边容器');
            return null;
        }

        // 如果fromNodeId或toNodeId不存在，给出警告
        if (!edge.fromNodeId || !edge.toNodeId) {
            console.warn('边的起始或目标节点ID缺失:', edge);
        }

        const edgeItem = $(`<div class="workflow-edge-item">
            <div class="form-row">
                <div class="form-group col-md-5">
                    <label>从节点</label>
                    <select class="form-control form-control-sm edge-from-select">
                        <option value="">-- 选择节点 --</option>
                    </select>
                </div>
                <div class="form-group col-md-5">
                    <label>到节点</label>
                    <select class="form-control form-control-sm edge-to-select">
                        <option value="">-- 选择节点 --</option>
                    </select>
                </div>
                <div class="form-group col-md-2">
                    <label>优先级</label>
                    <input type="number" class="form-control form-control-sm edge-priority-input" value="${edge.priority || 1}" min="1">
                </div>
            </div>
            <div class="form-row">
                <div class="form-group col-md-3">
                    <label>条件</label>
                    <select class="form-control form-control-sm edge-condition-type-select">
                        <option value="NONE" ${!edge.conditionType || edge.conditionType === 'NONE' ? 'selected' : ''}>无条件</option>
                        <option value="SUCCESS" ${edge.conditionType === 'SUCCESS' ? 'selected' : ''}>成功时</option>
                        <option value="FAILED" ${edge.conditionType === 'FAILED' ? 'selected' : ''}>失败时</option>
                        <option value="EXPRESSION" ${edge.conditionType === 'EXPRESSION' ? 'selected' : ''}>表达式</option>
                    </select>
                </div>
                <div class="form-group col-md-7 edge-expression-container" style="${edge.conditionType !== 'EXPRESSION' ? 'display:none;' : ''}">
                    <label>表达式</label>
                    <input type="text" class="form-control form-control-sm edge-expression-input" value="${edge.conditionExpression || ''}">
                </div>
                <div class="form-group col-md-2">
                    <label>&nbsp;</label>
                    <button type="button" class="btn btn-sm btn-danger form-control delete-edge-btn">删除</button>
                </div>
            </div>
        </div>`);

        container.append(edgeItem);

        // 更新此边的节点选择器
        if (typeof updateEdgeNodeSelectors === 'function') {
            updateEdgeNodeSelectors(edgeItem, edge.fromNodeId, edge.toNodeId);
        }

        // 绑定删除边按钮事件
        edgeItem.find('.delete-edge-btn').on('click', function() {
            edgeItem.remove();
            updateWorkflowEdgesJson();
            
            // 如果当前显示的是DAG标签页，更新工作流DAG图
            if ($('#workflow-dag-content').hasClass('active') && typeof updateWorkflowDag === 'function') {
                updateWorkflowDag();
            }
        });

        // 绑定输入变更事件
        edgeItem.find('input, select').on('change', function() {
            updateWorkflowEdgesJson();
            
            // 处理条件类型变更
            if ($(this).hasClass('edge-condition-type-select')) {
                const expressionContainer = edgeItem.find('.edge-expression-container');
                if ($(this).val() === 'EXPRESSION') {
                    expressionContainer.show();
                } else {
                    expressionContainer.hide();
                }
            }
        });

        return edgeItem;
    } catch (e) {
        console.error('添加工作流边时发生错误:', e);
        return null;
    }
}

/**
 * 更新所有边的节点选择器
 * 遍历所有边，更新其节点选择器
 */
function updateNodeSelectors() {
    try {
        $('.workflow-edge-item').each(function() {
            const fromSelect = $(this).find('.edge-from-select');
            const toSelect = $(this).find('.edge-to-select');
            
            // 保存当前选中的值
            const fromNodeId = fromSelect.val();
            const toNodeId = toSelect.val();
            
            // 清空选择器
            fromSelect.empty();
            toSelect.empty();
            
            // 添加默认选项
            fromSelect.append('<option value="">-- 选择节点 --</option>');
            toSelect.append('<option value="">-- 选择节点 --</option>');
            
            // 从已有节点项获取节点ID
            $('.workflow-node-item').each(function () {
                const nodeId = $(this).data('node-id');
                const nodeName = $(this).find('.node-name-input').val() || nodeId;
                
                fromSelect.append(`<option value="${nodeId}" ${nodeId === fromNodeId ? 'selected' : ''}>${nodeName}</option>`);
                toSelect.append(`<option value="${nodeId}" ${nodeId === toNodeId ? 'selected' : ''}>${nodeName}</option>`);
            });
        });
    } catch (e) {
        console.error('更新节点选择器时发生错误:', e);
    }
}

/**
 * 更新特定边的节点选择器
 * @param {jQuery} edgeItem - 边DOM元素
 * @param {string} fromNodeId - 源节点ID
 * @param {string} toNodeId - 目标节点ID
 */
function updateEdgeNodeSelectors(edgeItem, fromNodeId, toNodeId) {
    try {
        const fromSelect = edgeItem.find('.edge-from-select');
        const toSelect = edgeItem.find('.edge-to-select');
        
        // 清空选择器
        fromSelect.empty();
        toSelect.empty();
        
        // 添加默认选项
        fromSelect.append('<option value="">-- 选择节点 --</option>');
        toSelect.append('<option value="">-- 选择节点 --</option>');
        
        // 从已有节点项获取节点ID
        $('.workflow-node-item').each(function () {
            const nodeId = $(this).data('node-id');
            const nodeName = $(this).find('.node-name-input').val() || nodeId;
            
            fromSelect.append(`<option value="${nodeId}" ${nodeId === fromNodeId ? 'selected' : ''}>${nodeName}</option>`);
            toSelect.append(`<option value="${nodeId}" ${nodeId === toNodeId ? 'selected' : ''}>${nodeName}</option>`);
        });
    } catch (e) {
        console.error('更新特定边的节点选择器时发生错误:', e);
    }
}

/**
 * 更新工作流节点JSON
 * 收集所有节点DOM元素的数据，更新JSON文本区域
 */
function updateWorkflowNodesJson() {
    try {
        const nodes = [];
        
        $('.workflow-node-item').each(function() {
            const nodeId = $(this).find('.node-id-input').val();
            const nodeName = $(this).find('.node-name-input').val();
            const taskConfigId = $(this).find('.node-task-select').val();
            
            nodes.push({
                nodeId: nodeId,
                nodeName: nodeName,
                taskConfigId: taskConfigId ? parseInt(taskConfigId) : null
            });
        });
        
        $('#workflowNodesJson').val(JSON.stringify(nodes, null, 2));
    } catch (e) {
        console.error('更新工作流节点JSON时发生错误:', e);
    }
}

/**
 * 更新工作流边JSON
 * 收集所有边DOM元素的数据，更新JSON文本区域
 */
function updateWorkflowEdgesJson() {
    try {
        const edges = [];
        
        $('.workflow-edge-item').each(function() {
            const fromNodeId = $(this).find('.edge-from-select').val();
            const toNodeId = $(this).find('.edge-to-select').val();
            const priority = parseInt($(this).find('.edge-priority-input').val() || 1);
            const conditionType = $(this).find('.edge-condition-type-select').val();
            const conditionExpression = $(this).find('.edge-expression-input').val();
            
            // 只有当源节点和目标节点都选择了有效值时才添加
            if (fromNodeId && toNodeId) {
                const edge = {
                    fromNodeId: fromNodeId,
                    toNodeId: toNodeId,
                    priority: priority
                };
                
                // 只有在非"无条件"时才添加条件类型
                if (conditionType && conditionType !== 'NONE') {
                    edge.conditionType = conditionType;
                    
                    // 只有在条件类型为表达式时才添加表达式
                    if (conditionType === 'EXPRESSION' && conditionExpression) {
                        edge.conditionExpression = conditionExpression;
                    }
                }
                
                edges.push(edge);
            }
        });
        
        $('#workflowEdgesJson').val(JSON.stringify(edges, null, 2));
    } catch (e) {
        console.error('更新工作流边JSON时发生错误:', e);
    }
}

// 导出全局函数
window.WorkflowNodeEdgeHandler = {
    addWorkflowNodeItem: addWorkflowNodeItem,
    addWorkflowEdgeItem: addWorkflowEdgeItem,
    updateNodeSelectors: updateNodeSelectors,
    updateEdgeNodeSelectors: updateEdgeNodeSelectors,
    updateWorkflowNodesJson: updateWorkflowNodesJson,
    updateWorkflowEdgesJson: updateWorkflowEdgesJson
};
