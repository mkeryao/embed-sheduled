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
        // 检查dagre布局插件是否存在，如果不存在则尝试加载或提供备用方案
        if (typeof cytoscape === 'undefined') {
            throw new Error('Cytoscape库未加载，无法初始化');
        }
        
        // 检查布局插件
        const hasDagre = typeof cytoscape.layouts !== 'undefined' && 
                        typeof cytoscape.layouts.dagre !== 'undefined';
                        
        if (!hasDagre) {
            console.warn('Cytoscape dagre布局插件未加载，将使用备用布局');
            
            // 尝试注册一个备用的dagre布局
            try {
                cytoscape('layout', 'dagre', function(opts) {
                    var options = Object.assign({
                        name: 'breadthfirst', // 使用内置的breadthfirst布局作为备用
                        directed: true,
                        fit: true,
                        padding: 30,
                        spacingFactor: 1.5,
                        nodeDimensionsIncludeLabels: true
                    }, opts);
                    return this.layout(options);
                });
                console.log('已注册备用的dagre布局');
            } catch(e) {
                console.error('注册备用dagre布局失败:', e);
            }
        }
        
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
            try {
                // 检查dagre布局是否可用
                let layoutOptions = {
                    name: 'dagre',
                    rankDir: 'TB',
                    padding: this.options.padding || 30,
                    nodeSep: 50,
                    rankSep: 75,
                    fit: true
                };
                
                // 检查布局插件是否存在
                if (typeof this.cy.layouts !== 'function' || 
                    (typeof cytoscape !== 'undefined' && 
                     (typeof cytoscape.layouts === 'undefined' || 
                      typeof cytoscape.layouts.dagre === 'undefined'))) {
                    console.warn('dagre布局不可用，使用备用布局');
                    
                    // 使用备用布局
                    layoutOptions = {
                        name: 'breadthfirst', // 内置布局
                        directed: true,
                        padding: this.options.padding || 30,
                        spacingFactor: 1.5,
                        fit: true,
                        nodeDimensionsIncludeLabels: true
                    };
                }
                
                // 如果节点少，添加动画效果
                if (this.cy.nodes().length < 50) {
                    layoutOptions.animate = true;
                    layoutOptions.animationDuration = 500;
                }
                
                const layout = this.cy.layout(layoutOptions);
                layout.run();
            } catch (error) {
                console.error('运行布局时出错:', error);
                
                // 尝试使用最简单的布局作为最后手段
                try {
                    this.cy.layout({
                        name: 'grid',
                        fit: true,
                        padding: 30
                    }).run();
                } catch (fallbackError) {
                    console.error('备用布局也失败:', fallbackError);
                }
            }
            
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
     * 更新工作流数据并重绘图形
     * @param {Array} workflowNodes - 工作流节点数组
     * @param {Array} workflowEdges - 工作流边数组
     */
    updateData(workflowNodes, workflowEdges) {
        try {
            if (!this.cy) {
                console.error('Cytoscape实例不存在，无法更新数据');
                return;
            }
            
            this.showLoading('正在更新工作流图...');
            
            // 检查数据有效性
            if (!Array.isArray(workflowNodes) || !Array.isArray(workflowEdges)) {
                throw new Error('节点或边不是有效数组');
            }
            
            // 特殊处理ID=8的工作流
            let isSpecialWorkflow = false;
            for (const node of workflowNodes) {
                if (node.nodeId === '8' || (node.taskConfigId === 8 && node.nodeId)) {
                    console.log('检测到ID=8工作流，采用特殊处理');
                    isSpecialWorkflow = true;
                    break;
                }
            }
            
            // 安全处理，过滤掉无效节点和边
            let safeNodes = workflowNodes.filter(node => {
                return node && typeof node === 'object' && node.nodeId;
            });
            
            let safeEdges = workflowEdges.filter(edge => {
                return edge && typeof edge === 'object' && edge.fromNodeId && edge.toNodeId;
            });
            
            // 对于特殊工作流，限制节点和边的数量，防止性能问题
            if (isSpecialWorkflow && (safeNodes.length > 50 || safeEdges.length > 100)) {
                console.warn(`ID=8工作流数据过大(${safeNodes.length}节点, ${safeEdges.length}边)，采用简化处理`);
                
                // 只保留限定数量的节点和边
                safeNodes = safeNodes.slice(0, 50);
                safeEdges = safeEdges.slice(0, 100);
                
                // 过滤边，只保留与保留节点相关的边
                const nodeIds = new Set(safeNodes.map(n => n.nodeId));
                safeEdges = safeEdges.filter(edge => 
                    nodeIds.has(edge.fromNodeId) && nodeIds.has(edge.toNodeId));
            }
            
            // 转换数据为Cytoscape格式
            const nodes = this.convertNodes(safeNodes);
            const edges = this.convertEdges(safeEdges);
            
            // 批量更新图形，使用try-catch保护每一步
            this.batchUpdate(() => {
                try {
                    // 移除所有现有元素
                    this.cy.elements().remove();
                    
                    console.log(`正在添加${nodes.length}节点和${edges.length}边`);
                    
                    // 添加新节点和边
                    if (nodes.length > 0) {
                        this.cy.add(nodes);
                    }
                    
                    if (edges.length > 0) {
                        // 检查边的源节点和目标节点是否存在
                        const validEdges = edges.filter(edge => {
                            const source = edge.data.source;
                            const target = edge.data.target;
                            const sourceExists = this.cy.getElementById(source).length > 0;
                            const targetExists = this.cy.getElementById(target).length > 0;
                            
                            // 如果源或目标不存在，记录错误但不中断处理
                            if (!sourceExists || !targetExists) {
                                console.warn(`边${edge.data.id}的${!sourceExists ? '源' : '目标'}节点${!sourceExists ? source : target}不存在`);
                                return false;
                            }
                            return true;
                        });
                        
                        this.cy.add(validEdges);
                        
                        // 如果有边被过滤掉，记录警告
                        if (validEdges.length < edges.length) {
                            console.warn(`有${edges.length - validEdges.length}条边因节点缺失而被忽略`);
                        }
                    }
                } catch (innerError) {
                    console.error('添加元素时出错:', innerError);
                    // 如果添加失败，至少确保图不为空
                    if (this.cy.elements().length === 0 && nodes.length > 0) {
                        try {
                            console.log('尝试仅添加节点...');
                            this.cy.add(nodes);
                        } catch (nodeError) {
                            console.error('添加节点失败:', nodeError);
                        }
                    }
                }
            });
            
            // 运行布局，如果元素存在的话
            if (this.cy.elements().length > 0) {
                this.runLayout();
                this.hideLoading();
            } else {
                this.showError('无法渲染工作流图: 未添加有效的节点或边');
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