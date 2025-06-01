/**
 * WorkflowDagViewer - 一个基于Cytoscape的工作流程DAG可视化组件
 * 用于展示和（可选）编辑工作流节点和连接。
 * 
 * @version 1.2.0
 * @requires cytoscape.js
 * @requires cytoscape-dagre.js
 */

// 确保函数加载完毕后才尝试引用
document.addEventListener('DOMContentLoaded', function() {
    console.log('工作流DAG脚本已加载');
    // 在这里不执行任何初始化，只确保类定义被加载
});

// 定义WorkflowDagViewer类
class WorkflowDagViewer {
    /**
     * 创建一个新的WorkflowDagViewer实例
     * @param {string} containerId - 要挂载DAG图的DOM元素ID
     * @param {object} options - 配置选项
     * @param {boolean} options.readOnly - 是否为只读模式
     * @param {boolean} options.autoOptimize - 是否自动优化布局
     * @param {number} options.maxZoom - 最大缩放级别
     * @param {number} options.minZoom - 最小缩放级别
     */
    constructor(containerId, options = {}) {
        // 确保DOM已经加载完成
        if (document.readyState !== 'complete' && document.readyState !== 'interactive') {
            console.warn('DOM尚未完全加载，WorkflowDagViewer初始化可能不可靠');
        }

        // 验证containerId参数
        if (!containerId || typeof containerId !== 'string') {
            throw new Error('WorkflowDagViewer初始化失败: containerId必须是有效字符串');
        }
        
        // 设置默认选项和合并用户提供的选项
        this.options = Object.assign({
            readOnly: false,
            autoOptimize: true,
            maxZoom: 2.5,
            minZoom: 0.3,
            padding: 50,
            errorRecovery: true // 启用错误恢复功能
        }, options);
        
        // 初始化内部状态
        this._initialized = false;
        this._initializationAttempts = 0;
        this._maxInitAttempts = 3;
        this.containerId = containerId;
        
        // 创建一个loading状态指示器元素
        this.createLoadingIndicator(containerId);
        
        // 增强的容器检测和创建
        this.container = document.getElementById(containerId);
        
        // 检查容器是否存在，如果不存在则尝试创建一个
        if (!this.container) {
            console.warn(`容器 ${containerId} 不存在，正在尝试创建...`);
            
            // 首先尝试使用querySelector查找，以防ID格式不正确
            this.container = document.querySelector(`#${containerId}`) || document.querySelector(`.${containerId}`);
            
            if (!this.container) {
                // 创建一个新容器
                this.container = document.createElement('div');
                this.container.id = containerId;
                this.container.className = 'workflow-dag-container';
                
                // 查找潜在的父容器
                const parentContainers = [
                    '#workflow-dag-content',
                    '.workflow-dag-content',
                    '.modal-body',
                    '#workflowTaskFields',
                    '.dag-container-wrapper',
                    '.workflow-editor',
                    'main',
                    '.container',
                    'body'
                ];
                
                let parentFound = false;
                for (let selector of parentContainers) {
                    const parent = document.querySelector(selector);
                    if (parent) {
                        parent.appendChild(this.container);
                        console.log(`已将DAG图容器附加到${selector}`);
                        parentFound = true;
                        break;
                    }
                }
                
                // 如果没有找到任何父容器，附加到body
                if (!parentFound) {
                    document.body.appendChild(this.container);
                    console.warn('没有找到合适的父容器，已将DAG图容器直接附加到body');
                }
            }
        }
        
        // 确保容器有足够的高度和宽度来显示图形
        if (this.container) {
            // 检测容器的尺寸
            const containerRect = this.container.getBoundingClientRect();
            
            if (containerRect.height < 400) {
                this.container.style.minHeight = '400px';
                console.log('已将容器最小高度设置为400px');
            }
            
            if (containerRect.width < 300) {
                this.container.style.minWidth = '300px';
                console.log('已将容器最小宽度设置为300px');
            }
        } else {
            // 如果容器依然不存在，这是一个致命错误
            this.showError('无法初始化DAG图: 找不到或无法创建容器');
            throw new Error(`工作流DAG图初始化失败: 容器 ${containerId} 不存在且无法创建`);
        }
        
        // 检查是否有Cytoscape库
        if (typeof cytoscape === 'undefined') {
            this.showError('Cytoscape库未加载，请确保在页面中包含cytoscape.js');
            return;
        }
        
        // 检查是否有dagre布局插件
        if (typeof cytoscape.layouts.dagre === 'undefined') {
            this.showError('Cytoscape dagre布局插件未加载，请确保在页面中包含cytoscape-dagre.js');
            return;
        }
        
        // 显示加载状态
        this.showLoading('初始化DAG图...');
        
        // 初始化Cytoscape实例
        try {
            this.initCytoscape();
            this.hideLoading();
        } catch (error) {
            this.showError(`初始化工作流DAG图出错: ${error.message}`);
            console.error('初始化Cytoscape时出错:', error);
        }
    }
    
    /**
     * 创建一个加载指示器
     * @param {string} containerId - 容器ID
     */
    createLoadingIndicator(containerId) {
        // 检查是否已存在
        if (document.getElementById(`${containerId}-loading`)) {
            return;
        }
        
        const loading = document.createElement('div');
        loading.id = `${containerId}-loading`;
        loading.className = 'workflow-dag-loading';
        loading.innerHTML = `
            <div class="spinner-border text-primary" role="status">
                <span class="sr-only">加载中...</span>
            </div>
            <div class="loading-text mt-2">正在加载工作流图...</div>
        `;
        loading.style.display = 'none';
        
        // 添加到容器旁边
        const container = document.getElementById(containerId);
        if (container) {
            container.parentNode.insertBefore(loading, container.nextSibling);
        } else {
            // 如果容器不存在，添加到body
            document.body.appendChild(loading);
        }
        
        // 创建错误显示元素
        const error = document.createElement('div');
        error.id = `${containerId}-error`;
        error.className = 'workflow-dag-error alert alert-danger';
        error.style.display = 'none';
        
        // 添加到容器旁边
        if (container) {
            container.parentNode.insertBefore(error, loading.nextSibling);
        } else {
            document.body.appendChild(error);
        }
    }
    
    /**
     * 显示加载指示器
     * @param {string} message - 显示的消息
     */
    showLoading(message = '正在加载工作流图...') {
        const loading = document.getElementById(`${this.containerId}-loading`);
        if (loading) {
            loading.querySelector('.loading-text').textContent = message;
            loading.style.display = 'flex';
        }
    }
    
    /**
     * 隐藏加载指示器
     */
    hideLoading() {
        const loading = document.getElementById(`${this.containerId}-loading`);
        if (loading) {
            loading.style.display = 'none';
        }
    }
    
    /**
     * 显示错误消息
     * @param {string} message - 错误消息
     */
    showError(message) {
        const error = document.getElementById(`${this.containerId}-error`);
        if (error) {
            error.textContent = message;
            error.style.display = 'block';
        }
        this.hideLoading();
    }
    
    /**
     * 隐藏错误消息
     */
    hideError() {
        const error = document.getElementById(`${this.containerId}-error`);
        if (error) {
            error.style.display = 'none';
        }
    }
    
    /**
     * 初始化Cytoscape实例
     */
    initCytoscape() {
        // 初始化节点和边的样式
        const nodeStyles = [
            {
                selector: 'node',
                style: {
                    'background-color': '#4CAF50',
                    'border-color': '#388E3C',
                    'border-width': 2,
                    'label': 'data(label)',
                    'text-valign': 'center',
                    'text-halign': 'center',
                    'text-wrap': 'wrap',
                    'text-max-width': '100px',
                    'color': '#fff',
                    'font-size': '12px',
                    'height': '50px',
                    'width': '120px',
                    'shape': 'roundrectangle'
                }
            },
            {
                selector: 'node[status = "SUCCESS"]',
                style: {
                    'background-color': '#4CAF50',
                    'border-color': '#388E3C'
                }
            },
            {
                selector: 'node[status = "FAILED"]',
                style: {
                    'background-color': '#F44336',
                    'border-color': '#D32F2F'
                }
            },
            {
                selector: 'node[status = "RUNNING"]',
                style: {
                    'background-color': '#2196F3',
                    'border-color': '#1976D2'
                }
            },
            {
                selector: 'node[status = "WAITING"]',
                style: {
                    'background-color': '#FFC107',
                    'border-color': '#FFA000'
                }
            }
        ];
        
        const edgeStyles = [
            {
                selector: 'edge',
                style: {
                    'width': 2,
                    'line-color': '#ccc',
                    'target-arrow-color': '#ccc',
                    'target-arrow-shape': 'triangle',
                    'curve-style': 'bezier',
                    'label': 'data(label)',
                    'font-size': '10px',
                    'text-rotation': 'autorotate',
                    'text-margin-y': '-6px',
                    'text-background-color': 'white',
                    'text-background-opacity': 0.7,
                    'text-background-padding': '2px'
                }
            },
            {
                selector: 'edge[status = "SUCCESS"]',
                style: {
                    'line-color': '#4CAF50',
                    'target-arrow-color': '#4CAF50'
                }
            },
            {
                selector: 'edge[status = "FAILED"]',
                style: {
                    'line-color': '#F44336',
                    'target-arrow-color': '#F44336'
                }
            },
            {
                selector: 'edge[status = "RUNNING"]',
                style: {
                    'line-color': '#2196F3',
                    'target-arrow-color': '#2196F3'
                }
            },
            {
                selector: 'edge[status = "WAITING"]',
                style: {
                    'line-style': 'dashed'
                }
            }
        ];
        
        // 移除容器内的原有内容
        if (this.container) {
            while (this.container.firstChild) {
                this.container.removeChild(this.container.firstChild);
            }
        }
        
        try {
            // 创建Cytoscape实例
            this.cy = cytoscape({
                container: this.container,
                elements: [], // 初始化时为空
                style: [...nodeStyles, ...edgeStyles],
                layout: {
                    name: 'dagre',
                    rankDir: 'TB', // 从上到下（top to bottom）
                    nodeSep: 50, // 节点间的最小距离
                    rankSep: 75, // 层与层之间的距离
                    padding: this.options.padding,
                    fit: true
                },
                minZoom: this.options.minZoom,
                maxZoom: this.options.maxZoom
            });
            
            // 添加视图控制，如果不是只读模式
            if (!this.options.readOnly) {
                this.addViewControls();
            }
            
            // 添加节点的工具提示功能
            this.addTooltipFunctions();
        } catch (error) {
            console.error('Cytoscape初始化失败:', error);
            throw error; // 重新抛出以便外部捕获
        }
    }
    
    /**
     * 添加节点工具提示功能
     */
    addTooltipFunctions() {
        let tooltipTimeout;
        const tooltipId = `${this.containerId}-tooltip`;
        
        // 创建工具提示元素，如果不存在
        let tooltip = document.getElementById(tooltipId);
        if (!tooltip) {
            tooltip = document.createElement('div');
            tooltip.id = tooltipId;
            tooltip.className = 'workflow-dag-tooltip';
            tooltip.style.position = 'absolute';
            tooltip.style.display = 'none';
            tooltip.style.zIndex = '1000';
            tooltip.style.backgroundColor = 'white';
            tooltip.style.border = '1px solid #ccc';
            tooltip.style.borderRadius = '4px';
            tooltip.style.padding = '5px';
            tooltip.style.boxShadow = '0 2px 5px rgba(0, 0, 0, 0.2)';
            document.body.appendChild(tooltip);
        }
        
        // 鼠标悬停时显示工具提示
        this.cy.on('mouseover', 'node', (event) => {
            const node = event.target;
            const pos = event.renderedPosition;
            const data = node.data();
            
            tooltipTimeout = setTimeout(() => {
                tooltip.innerHTML = `
                    <div><strong>节点ID:</strong> ${data.id || 'N/A'}</div>
                    ${data.nodeName ? `<div><strong>节点名称:</strong> ${data.nodeName}</div>` : ''}
                    ${data.taskConfigId ? `<div><strong>任务ID:</strong> ${data.taskConfigId}</div>` : ''}
                    ${data.status ? `<div><strong>状态:</strong> ${data.status}</div>` : ''}
                    ${data.parameters ? `<div><strong>参数:</strong> <pre>${JSON.stringify(data.parameters, null, 2)}</pre></div>` : ''}
                `;
                
                const containerRect = this.container.getBoundingClientRect();
                tooltip.style.left = (containerRect.left + pos.x) + 'px';
                tooltip.style.top = (containerRect.top + pos.y + 10) + 'px';
                tooltip.style.display = 'block';
            }, 500); // 延迟显示工具提示
        });
        
        // 边的工具提示
        this.cy.on('mouseover', 'edge', (event) => {
            const edge = event.target;
            const mid = edge.midpoint();
            const pos = { x: mid.x, y: mid.y };
            const data = edge.data();
            
            tooltipTimeout = setTimeout(() => {
                tooltip.innerHTML = `
                    <div><strong>从节点:</strong> ${data.source}</div>
                    <div><strong>到节点:</strong> ${data.target}</div>
                    ${data.expression ? `<div><strong>条件:</strong> ${data.expression}</div>` : ''}
                    ${data.priority ? `<div><strong>优先级:</strong> ${data.priority}</div>` : ''}
                    ${data.status ? `<div><strong>状态:</strong> ${data.status}</div>` : ''}
                `;
                
                const containerRect = this.container.getBoundingClientRect();
                tooltip.style.left = (containerRect.left + pos.x) + 'px';
                tooltip.style.top = (containerRect.top + pos.y + 10) + 'px';
                tooltip.style.display = 'block';
            }, 500);
        });
        
        // 鼠标移出时隐藏工具提示
        this.cy.on('mouseout', 'node, edge', () => {
            clearTimeout(tooltipTimeout);
            tooltip.style.display = 'none';
        });
    }
    
    /**
     * 添加视图控制（缩放、居中等）
     */
    addViewControls() {
        const containerId = this.containerId;
        
        // 检查是否已存在控制面板
        if (document.getElementById(`${containerId}-controls`)) {
            return;
        }
        
        // 创建控制面板
        const controls = document.createElement('div');
        controls.id = `${containerId}-controls`;
        controls.className = 'workflow-dag-controls';
        controls.innerHTML = `
            <button class="btn btn-sm btn-light zoom-in" title="放大">
                <i class="fas fa-search-plus"></i>
            </button>
            <button class="btn btn-sm btn-light zoom-out" title="缩小">
                <i class="fas fa-search-minus"></i>
            </button>
            <button class="btn btn-sm btn-light center" title="居中">
                <i class="fas fa-compress-arrows-alt"></i>
            </button>
            <button class="btn btn-sm btn-light refresh" title="刷新布局">
                <i class="fas fa-sync"></i>
            </button>
        `;
        
        // 添加到容器
        if (this.container && this.container.parentNode) {
            this.container.parentNode.insertBefore(controls, this.container);
        }
        
        // 添加事件处理
        document.querySelector(`#${containerId}-controls .zoom-in`).addEventListener('click', () => {
            this.cy.zoom({
                level: this.cy.zoom() * 1.2,
                renderedPosition: { x: this.container.clientWidth / 2, y: this.container.clientHeight / 2 }
            });
        });
        
        document.querySelector(`#${containerId}-controls .zoom-out`).addEventListener('click', () => {
            this.cy.zoom({
                level: this.cy.zoom() / 1.2,
                renderedPosition: { x: this.container.clientWidth / 2, y: this.container.clientHeight / 2 }
            });
        });
        
        document.querySelector(`#${containerId}-controls .center`).addEventListener('click', () => {
            this.cy.fit(undefined, this.options.padding);
        });
        
        document.querySelector(`#${containerId}-controls .refresh`).addEventListener('click', () => {
            this.runLayout();
        });
    }
    
    /**
     * 重新运行布局算法
     */
    runLayout() {
        this.showLoading('正在优化布局...');
        
        // 使用setTimeout以便先显示加载状态
        setTimeout(() => {
            // 使用dagre布局
            const layout = this.cy.layout({
                name: 'dagre',
                rankDir: 'TB',
                padding: this.options.padding,
                nodeSep: 50,
                rankSep: 75,
                fit: true,
                animate: true,
                animationDuration: 500
            });
            
            layout.run();
            this.hideLoading();
        }, 10);
    }
    
    /**
     * 批量更新视图，避免频繁重绘
     * @private
     * @param {function} updateFn - 更新函数
     */
    batchUpdate(updateFn) {
        this.cy.batch(updateFn);
    }
    
    /**
     * 转换工作流节点为Cytoscape节点
     * @private
     * @param {Array} workflowNodes - 工作流节点数组
     * @returns {Array} Cytoscape节点数组
     */
    convertNodes(workflowNodes) {
        return workflowNodes.map(node => {
            return {
                data: {
                    id: node.nodeId,
                    label: node.nodeName || node.nodeId,
                    nodeName: node.nodeName,
                    taskConfigId: node.taskConfigId,
                    parameters: node.parameters,
                    status: node.status || 'WAITING'
                }
            };
        });
    }
    
    /**
     * 转换工作流边为Cytoscape边
     * @private
     * @param {Array} workflowEdges - 工作流边数组
     * @returns {Array} Cytoscape边数组
     */
    convertEdges(workflowEdges) {
        return workflowEdges.map(edge => {
            const edgeId = `${edge.fromNodeId}-${edge.toNodeId}`;
            const label = edge.expression ? this.formatExpressionLabel(edge.expression) : '';
            
            return {
                data: {
                    id: edgeId,
                    source: edge.fromNodeId,
                    target: edge.toNodeId,
                    label: label,
                    expression: edge.expression,
                    priority: edge.priority,
                    status: edge.status || 'WAITING'
                }
            };
        });
    }
    
    /**
     * 格式化表达式为简短标签
     * @private
     * @param {string} expression - 表达式
     * @returns {string} 格式化后的标签
     */
    formatExpressionLabel(expression) {
        if (!expression) return '';
        
        // 裁剪表达式，使其不会太长
        const maxLength = 20;
        if (expression.length > maxLength) {
            return expression.substring(0, maxLength) + '...';
        }
        return expression;
    }
    
    /**
     * 使用新数据更新DAG图
     * @param {Array} nodes - 工作流节点数组
     * @param {Array} edges - 工作流边数组
     */
    updateData(nodes, edges) {
        // 安全检查输入
        if (!Array.isArray(nodes) || !Array.isArray(edges)) {
            this.showError('工作流数据格式错误: 节点和边必须是数组');
            console.error('工作流数据格式错误:', { nodes, edges });
            return;
        }
        
        // 防止处理过大的数据集导致浏览器卡死
        if (nodes.length > 500 || edges.length > 1000) {
            this.showLoading(`正在加载大型工作流 (${nodes.length}节点, ${edges.length}边)...`);
        }
        
        try {
            // 隐藏之前的错误
            this.hideError();
            
            // 使用批量更新提高性能
            this.batchUpdate(() => {
                // 清空当前图形
                this.cy.elements().remove();
                
                // 转换节点和边为Cytoscape格式
                const cyNodes = this.convertNodes(nodes);
                const cyEdges = this.convertEdges(edges);
                
                // 添加节点和边
                if (cyNodes.length > 0) {
                    this.cy.add(cyNodes);
                }
                
                if (cyEdges.length > 0) {
                    this.cy.add(cyEdges);
                }
            });
            
            // 运行布局
            if (nodes.length > 0 || edges.length > 0) {
                this.runLayout();
            }
        } catch (error) {
            this.showError(`更新工作流DAG图出错: ${error.message}`);
            console.error('更新DAG数据时出错:', error);
        }
    }
    
    /**
     * 清理资源，当不再需要DAG图时调用
     */
    destroy() {
        // 清理Cytoscape实例，释放内存
        if (this.cy) {
            this.cy.destroy();
            this.cy = null;
        }
        
        // 移除控制面板
        const controls = document.getElementById(`${this.containerId}-controls`);
        if (controls) {
            controls.parentNode.removeChild(controls);
        }
        
        // 移除工具提示
        const tooltip = document.getElementById(`${this.containerId}-tooltip`);
        if (tooltip) {
            tooltip.parentNode.removeChild(tooltip);
        }
        
        // 移除加载指示器
        const loading = document.getElementById(`${this.containerId}-loading`);
        if (loading) {
            loading.parentNode.removeChild(loading);
        }
        
        // 移除错误提示
        const error = document.getElementById(`${this.containerId}-error`);
        if (error) {
            error.parentNode.removeChild(error);
        }
    }
}

// 添加全局函数用于初始化DAG图
function initWorkflowDag() {
    console.log('全局初始化函数被调用');
    
    if (typeof window.initWorkflowDag === 'function') {
        // 如果已经有一个函数定义，则调用它
        return window.initWorkflowDag();
    }
    
    // 否则尝试在tasks.html页面上初始化
    if (document.getElementById('workflow-dag-view')) {
        console.log('找到工作流DAG图容器，尝试初始化');
        
        // 检查Cytoscape库是否已加载
        if (typeof cytoscape === 'undefined') {
            console.error('Cytoscape库未加载，无法初始化DAG图');
            return;
        }
        
        // 创建新的DAG图实例
        try {
            if (typeof WorkflowDagViewer !== 'undefined') {
                window.workflowDag = new WorkflowDagViewer('workflow-dag-view', {
                    readOnly: false,
                    autoOptimize: true
                });
                console.log('DAG图初始化成功');
            } else {
                console.error('WorkflowDagViewer未定义');
            }
        } catch (e) {
            console.error('DAG图初始化失败', e);
        }
    } else {
        console.warn('未找到工作流DAG图容器');
    }
}

// 添加验证Cron表达式的函数
function validateCronExpression() {
    console.log('验证Cron表达式');
    const cronValue = $('#cronExpression').val();
    if (!cronValue) return;

    $.ajax({
        url: `/api/tasks/validate-cron?cronExpression=${encodeURIComponent(cronValue)}`,
        method: 'GET',
        headers: {
            'Authorization': 'Bearer ' + localStorage.getItem('jwtToken')
        },
        dataType: 'json',
        success: function(data) {
            const validationDiv = $('#cronValidation');
            if (!validationDiv.length) {
                $('#cronExpression').after('<div id="cronValidation" class="cron-validation"></div>');
            }

            if (data.valid) {
                $('#cronValidation').html('<i class="fas fa-check-circle"></i> 表达式有效').removeClass('invalid').addClass('valid');
            } else {
                $('#cronValidation').html('<i class="fas fa-exclamation-circle"></i> 表达式无效').removeClass('valid').addClass('invalid');
            }
        },
        error: function() {
            $('#cronValidation').html('<i class="fas fa-exclamation-circle"></i> 验证失败').removeClass('valid').addClass('invalid');
        }
    });
}

// 确保函数全局可用
window.initWorkflowDag = initWorkflowDag;
window.validateCronExpression = validateCronExpression;