/**
 * tasks-workflow.js
 * 工作流图形绘制脚本
 */

(function() {
    // 避免重复初始化
    if (typeof window.tasksWorkflowInitialized !== 'undefined') {
        console.log('tasks workflow脚本已初始化，跳过重复初始化');
        return;
    }

    console.log('初始化tasks workflow脚本...');
    window.tasksWorkflowInitialized = true;
    
    // 图形模式变量
    window.graphModeEnabled = false;
    window.currentScale = 1.0;
    window.svgDraggable = false;
    window.nodePositionChanged = false;
    window.nodeDragging = false;
    window.currentDragNode = null;
    window.nodePositionsMap = window.nodePositionsMap || {};
    
    // 防止递归调用和设置安全超时
    window.redrawDAGCounter = 0;
    window.isRedrawingDAG = false;
    window.redrawDagThrottleTimer = null;

    /**
     * 初始化SVG容器
     * @private
     */
    function initializeSvg(container, svgNS) {
        const svg = $(document.createElementNS(svgNS, 'svg')).attr({
            'width': '1200',
            'height': '800',
            'class': 'workflow-dag-svg',
            'viewBox': '0 0 1200 800'
        });
            
        // 创建箭头标记定义
        const defs = $(document.createElementNS(svgNS, 'defs'));
        const marker = $(document.createElementNS(svgNS, 'marker')).attr({
            'id': 'arrowhead',
            'viewBox': '0 0 10 10',
            'refX': '8',
            'refY': '5',
            'markerWidth': '6',
            'markerHeight': '6',
            'orient': 'auto'
        });

        const markerPath = $(document.createElementNS(svgNS, 'path')).attr({
            'd': 'M 0 0 L 10 5 L 0 10 z',
            'fill': '#555'
        });

        marker.append(markerPath);
        defs.append(marker);
        svg.append(defs);
        container.empty().append(svg);
        
        return svg;
    }    /**
     * 重绘DAG图的核心函数
     * @private
     */
    function performRedrawDAG(enableDragging) {
        let safetyTimeout;
        try {
            // 防止正在进行的绘制过程
            if (window.isRedrawingDAG) {
                console.warn('DAG重绘已在进行中，跳过本次重绘请求');
                return;
            }
            
            window.isRedrawingDAG = true;
            
            // 设置安全超时，防止卡死
            safetyTimeout = setTimeout(function() {
                console.warn('重绘DAG超时，重置状态');
                window.isRedrawingDAG = false;
                window.redrawDAGCounter = Math.max(0, window.redrawDAGCounter - 1);
            }, 10000);
            
            const dagContainer = $('#dagContainer');
            // 确保dagContainer存在
            if (dagContainer.length === 0) {
                console.error('找不到DAG容器元素');
                clearTimeout(safetyTimeout);
                window.isRedrawingDAG = false;
                return;
            }
            
            const nodesJson = $('#workflowNodesJson').val();
            
            if (!nodesJson || nodesJson.trim() === '') {
                dagContainer.html('<div class="text-muted">没有节点数据可显示。点击"添加节点"开始创建工作流。</div>');
                clearTimeout(safetyTimeout);
                window.isRedrawingDAG = false;
                return;
            }
            
            let nodes;
            try {
                nodes = JSON.parse(nodesJson);
                if (window.debugMode) {
                    console.log(`解析出${nodes.length}个节点`);
                }
            } catch (e) {
                console.error('解析节点JSON失败:', e);
                dagContainer.html(`<div class="alert alert-danger">解析工作流节点数据失败: ${e.message}</div>`);
                clearTimeout(safetyTimeout);
                window.isRedrawingDAG = false;
                return;
            }
            
            if (!Array.isArray(nodes) || nodes.length === 0) {
                dagContainer.html('<div class="text-muted">没有节点数据。请添加至少一个节点。</div>');
                return;
            }

            // 初始化SVG容器
            const svgNS = 'http://www.w3.org/2000/svg';
            const svg = initializeSvg(dagContainer, svgNS);

            // 清空现有内容，保留defs
            svg.children().not('defs').remove();

            // 创建节点组
            nodes.forEach((node, index) => {
                const nodeGroup = document.createElementNS(svgNS, 'g');
                const x = window.nodePositionsMap[node.id] ? window.nodePositionsMap[node.id].x : (100 + index * 200);
                const y = window.nodePositionsMap[node.id] ? window.nodePositionsMap[node.id].y : 100;


                $(nodeGroup).attr({
                    'class': 'workflow-node',
                    'data-node-id': node.id,
                    'transform': `translate(${x},${y})`
                });

                // 创建节点矩形
                const rect = document.createElementNS(svgNS, 'rect');
                $(rect).attr({
                    'width': '150',
                    'height': '60',
                    'rx': '5',
                    'ry': '5',
                    'fill': '#fff',
                    'stroke': '#666',
                    'stroke-width': '2'
                });

                // 创建节点文本
                const text = document.createElementNS(svgNS, 'text');
                $(text).attr({
                    'x': '75',
                    'y': '35',
                    'text-anchor': 'middle',
                    'fill': '#333'
                }).text(node.name || node.id);

                nodeGroup.appendChild(rect);
                nodeGroup.appendChild(text);
                svg.append(nodeGroup);

                // 保存节点位置信息
                window.nodePositionsMap[node.id] = { x, y };
            });

            // 绘制边缘连接
            if (window.drawWorkflowEdges) {
                window.drawWorkflowEdges(svg[0], window.nodePositionsMap);
            }

            // 启用拖拽功能
            if (enableDragging && window.graphModeEnabled) {
                svg.find('.workflow-node').draggable({
                    start: function(event, ui) {
                        window.nodeDragging = true;
                        const nodeId = $(this).data('node-id');
                        ui.helper.data('original-node-pos', nodeId);
                    },
                    drag: function(event, ui) {
                        const nodeId = ui.helper.data('original-node-pos');
                        if (!nodeId) return;

                        const originalPos = window.nodePositionsMap[nodeId];
                        if (!originalPos) return;

                        const svgElement = $(this).closest('svg');
                        const viewBox = svgElement.attr('viewBox').split(' ').map(Number);
                        const scale = viewBox[2] / svgElement.width();

                        const newX = originalPos.x + (ui.position.left - ui.originalPosition.left) * scale;
                        const newY = originalPos.y + (ui.position.top - ui.originalPosition.top) * scale;

                        // Update transform for visual feedback of the node itself
                        this.setAttribute('transform', `translate(${newX}, ${newY})`);

                        // Use a temporary positions map to redraw edges without persisting
                        const tempPositions = { ...window.nodePositionsMap };
                        tempPositions[nodeId] = { x: newX, y: newY };
                        window.drawWorkflowEdges(svgElement[0], tempPositions);
                    },
                    stop: function(event, ui) {
                        window.nodeDragging = false;
                        const nodeId = ui.helper.data('original-node-pos');
                        if (!nodeId) return;

                        const originalPos = window.nodePositionsMap[nodeId];
                        if (!originalPos) return;

                        const svgElement = $(this).closest('svg');
                        const viewBox = svgElement.attr('viewBox').split(' ').map(Number);
                        const scale = viewBox[2] / svgElement.width();

                        const finalX = originalPos.x + (ui.position.left - ui.originalPosition.left) * scale;
                        const finalY = originalPos.y + (ui.position.top - ui.originalPosition.top) * scale;

                        // Persist the final position
                        window.nodePositionsMap[nodeId] = { x: finalX, y: finalY };
                        
                        // Reset helper position
                        ui.helper.css({top: 0, left: 0});

                        // Final redraw with persisted positions
                        window.safeRedrawDAG(true);
                    }
                });
            }

            // 添加SVG画布的拖动功能
            if (window.graphModeEnabled) {
                enableSvgPanning(svg);
            }

            // 触发重绘完成事件
            $(document).trigger('workflow:dagRedrawn');
        } catch (e) {
            console.error('重绘DAG图时出错:', e);
            $('#dagContainer').html(`<div class="alert alert-danger">绘制工作流图失败: ${e.message}</div>`);
        } finally {
            window.isRedrawingDAG = false;
            if (safetyTimeout) {
                clearTimeout(safetyTimeout);
            }
            if (window.redrawDAGCounter > 0) {
                window.redrawDAGCounter--;
            }
        }
    }

    /**
     * 显示反馈信息
     * @param {string} message 消息内容
     * @param {boolean} isError 是否是错误信息
     */
    function showFeedback(message, isError) {
        if (typeof message !== 'string' || message.length === 0) return;
        
        try {
            // 尝试使用main.js中的显示反馈函数
            if (typeof window.showToast === 'function') {
                window.showToast(message, isError ? 'error' : 'success');
                return;
            }
            
            console.log(isError ? 'Error: ' + message : 'Success: ' + message);
            
            // 简易备用实现
            const alertType = isError ? 'danger' : 'success';
            const iconClass = isError ? 'bi-exclamation-triangle-fill' : 'bi-check-circle-fill';
            const alertId = 'workflow-feedback-alert';
            
            // 移除存在的反馈
            $('#' + alertId).remove();
            
            // 创建新的反馈元素
            const alert = $('<div>')
                .attr('id', alertId)
                .addClass(`alert alert-${alertType} alert-dismissible fade show`)
                .css({
                    position: 'fixed',
                    top: '20px',
                    right: '20px',
                    zIndex: 2000,
                    maxWidth: '80%'
                })
                .html(`
                    <i class="bi ${iconClass} me-2"></i> ${message}
                    <button type="button" class="close" data-dismiss="alert" aria-label="Close">
                        <span aria-hidden="true">&times;</span>
                    </button>
                `);
                
            // 添加到文档中
            $('body').append(alert);
            
            // 设置自动关闭
            setTimeout(() => {
                alert.alert('close');
            }, 5000);
        } catch (e) {
            console.error('显示反馈时发生错误:', e);
            console.log(isError ? 'Error: ' + message : 'Success: ' + message);
        }
    }
    
    /**
     * 安全重绘DAG图
     * 包含防止递归调用的保护措施
     * @param {boolean} [enableDragging=false] 是否启用节点拖动
     */
    window.safeRedrawDAG = function(enableDragging) {
        try {
            // 防止重入
            if (window.isRedrawingDAG) {
                console.warn('已有一个重绘DAG的操作正在进行中');
                return;
            }

            // 设置状态并记录计数
            window.isRedrawingDAG = true;
            window.redrawDAGCounter++;

            // 安全性检查 - 如果重绘次数太多，可能存在循环问题
            if (window.redrawDAGCounter > 10) {
                console.warn('检测到可能的DAG重绘递归，跳过此次重绘');
                window.isRedrawingDAG = false;
                window.redrawDAGCounter = 0;
                return;
            }

            // 延迟执行，以确保UI有机会更新
            if (window.redrawDagThrottleTimer) {
                clearTimeout(window.redrawDagThrottleTimer);
            }
            
            window.redrawDagThrottleTimer = setTimeout(function() {
                try {
                    // 调用实际的redrawDAG函数
                    if (typeof window.redrawDAG === 'function') {
                        window.redrawDAG(enableDragging);
                    } else {
                        console.error('未找到redrawDAG函数');
                    }
                } catch (e) {
                    console.error('执行redrawDAG时出错:', e);
                } finally {
                    // 完成后重置状态
                    window.isRedrawingDAG = false;
                }
            }, 100);
        } catch (e) {
            console.error('safeRedrawDAG出错:', e);
            window.isRedrawingDAG = false;
        }
    };

    /**
     * 重绘DAG图的公共函数
     */
    window.redrawDAG = function(enableDragging) {
        if (!window.redrawDAGCounter) {
            window.redrawDAGCounter = 0;
        }
        
        if (window.redrawDAGCounter > 3) {
            console.warn('检测到过多的连续redrawDAG调用，强制冷却');
            setTimeout(function() {
                window.redrawDAGCounter = 0;
            }, 2000);
            return;
        }
        
        window.redrawDAGCounter++;
        
        if (window.isRedrawingDAG) {
            console.warn('检测到递归调用redrawDAG，已阻止');
            return;
        }
        
        if (window.redrawDagThrottleTimer) {
            clearTimeout(window.redrawDagThrottleTimer);
        }
        
        window.redrawDagThrottleTimer = setTimeout(function() {
            try {
                performRedrawDAG(enableDragging);
            } catch (err) {
                console.error('执行DAG重绘时发生错误:', err);
                window.isRedrawingDAG = false;
                window.redrawDAGCounter = Math.max(0, window.redrawDAGCounter - 1);
            }
        }, 50);
    };

    /**
     * 为SVG画布启用拖动功能
     * @private
     */
    function enableSvgPanning(svg) {
        if (!svg || svg.data('panning-enabled')) {
            return;
        }
        
        let isPanning = false;
        let startX, startY;
        let viewBox = { x: 0, y: 0, width: 1200, height: 800 };
        
        if (!svg.attr('viewBox')) {
            svg.attr('viewBox', `${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`);
        } else {
            const vbValues = svg.attr('viewBox').split(' ').map(parseFloat);
            viewBox = {
                x: vbValues[0],
                y: vbValues[1],
                width: vbValues[2],
                height: vbValues[3]
            };
        }
        
        svg.on('mousedown', function(e) {
            if (e.which === 1 && !window.nodeDragging && window.graphModeEnabled && window.svgDraggable) {
                isPanning = true;
                startX = e.clientX;
                startY = e.clientY;
                e.preventDefault();
                svg.css('cursor', 'grabbing');
            }
        });
        
        $(document).on('mousemove', function(e) {
            if (isPanning) {
                const dx = (e.clientX - startX) * (viewBox.width / svg.width());
                const dy = (e.clientY - startY) * (viewBox.height / svg.height());
                
                viewBox.x -= dx;
                viewBox.y -= dy;
                
                svg.attr('viewBox', `${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`);
                
                startX = e.clientX;
                startY = e.clientY;
            }
        });
        
        $(document).on('mouseup', function() {
            if (isPanning) {
                isPanning = false;
                svg.css('cursor', 'grab');
            }
        });
        
        svg.data('panning-enabled', true);
        
        if (window.svgDraggable) {
            svg.css('cursor', 'grab');
        }
    }

    /**
     * 绘制工作流节点之间的边缘连线
     */
    window.drawWorkflowEdges = function(svg, nodePositions) {
        if (!svg || !nodePositions) {
            console.warn('绘制边缘连接线失败：无效的参数');
            return;
        }
        
        try {
            const edgesJson = $('#workflowEdgesJson').val();
            if (!edgesJson || edgesJson.trim() === '') {
                console.log('没有边缘数据需要绘制');
                return;
            }
            
            let edges;
            try {
                edges = JSON.parse(edgesJson);
                console.log(`解析出 ${edges.length} 条边缘连接`);
            } catch (e) {
                console.error('解析边缘JSON失败:', e);
                return;
            }
            
            if (!Array.isArray(edges) || edges.length === 0) {
                console.log('没有有效的边缘数据');
                return;
            }
            
            // 移除现有的边缘线
            $(svg).find('path.workflow-edge').remove();
            $(svg).find('g.edge-label').remove();
            
            const svgNS = 'http://www.w3.org/2000/svg';
            
            edges.forEach(function(edge, index) {
                if (!edge.fromNode || !edge.toNode) {
                    console.warn(`跳过边缘 #${index}：缺少源节点或目标节点ID`);
                    return;
                }
                
                const sourcePos = nodePositions[edge.fromNode];
                const targetPos = nodePositions[edge.toNode];
                
                if (!sourcePos || !targetPos) {
                    console.warn(`跳过边缘 ${edge.fromNode} -> ${edge.toNode}：找不到节点位置`);
                    return;
                }
                
                const path = document.createElementNS(svgNS, 'path');
                const dx = Math.abs(targetPos.x - sourcePos.x);
                const dy = Math.abs(targetPos.y - sourcePos.y);
                const controlLength = Math.min(100, Math.max(30, Math.sqrt(dx * dx + dy * dy) * 0.5));
                
                let sourceControlX = sourcePos.x + (targetPos.x > sourcePos.x ? controlLength : -controlLength);
                let targetControlX = targetPos.x + (targetPos.x > sourcePos.x ? -controlLength : controlLength);
                let sourceControlY = sourcePos.y + (targetPos.y > sourcePos.y ? controlLength * 0.3 : -controlLength * 0.3);
                let targetControlY = targetPos.y + (targetPos.y > sourcePos.y ? -controlLength * 0.3 : controlLength * 0.3);
                
                const pathData = `M ${sourcePos.x} ${sourcePos.y} C ${sourceControlX} ${sourceControlY}, ${targetControlX} ${targetControlY}, ${targetPos.x} ${targetPos.y}`;
                
                $(path).attr({
                    'd': pathData,
                    'stroke': edge.expression ? '#28a745' : '#555',
                    'stroke-width': edge.priority ? 2 + Math.min(3, parseInt(edge.priority)) : 2,
                    'fill': 'none',
                    'marker-end': 'url(#arrowhead)',
                    'class': 'workflow-edge',
                    'data-from-node': edge.fromNode,
                    'data-to-node': edge.toNode
                });
                
                if (edge.expression) {
                    $(path).attr('stroke-dasharray', '5,3');
                }
                
                $(svg).append(path);
                
                // 添加边缘标签
                if ((edge.expression || edge.priority) && window.graphModeEnabled) {
                    const labelG = document.createElementNS(svgNS, 'g');
                    $(labelG).addClass('edge-label');
                    
                    const midX = (sourcePos.x + targetPos.x) / 2;
                    const midY = (sourcePos.y + targetPos.y) / 2;
                    
                    let labelContent = '';
                    if (edge.expression) {
                        labelContent += `E: ${edge.expression.substring(0, 8)}`;
                        if (edge.expression.length > 8) labelContent += '...';
                    }
                    if (edge.priority) {
                        if (labelContent) labelContent += ', ';
                        labelContent += `P: ${edge.priority}`;
                    }
                    
                    const labelBg = document.createElementNS(svgNS, 'rect');
                    const labelText = document.createElementNS(svgNS, 'text');
                    
                    const textWidth = labelContent.length * 5.5 + 10;
                    const textHeight = 16;
                    
                    $(labelBg).attr({
                        'x': -textWidth/2,
                        'y': -textHeight/2,
                        'width': textWidth,
                        'height': textHeight,
                        'fill': '#007bff',
                        'rx': 3
                    });
                    
                    $(labelText).attr({
                        'x': -textWidth/2 + 5,
                        'y': textHeight/4,
                        'fill': '#fff',
                        'font-size': '9px'
                    }).text(labelContent);
                    
                    labelG.appendChild(labelBg);
                    labelG.appendChild(labelText);
                    $(labelG).attr('transform', `translate(${midX},${midY})`);
                    
                    $(svg).append(labelG);
                }
            });
        } catch (e) {
            console.error('绘制边缘连接线时出错:', e);
        }
    };

    /**
     * 节点管理函数
     */    window.addWorkflowNode = function(nodeData) {
        try {
            if (!nodeData || !nodeData.id) {
                throw new Error('节点数据不完整，必须包含ID');
            }
            
            let nodes = [];
            const nodesJson = $('#workflowNodesJson').val();
            if (nodesJson && nodesJson.trim() !== '') {
                try {
                    nodes = JSON.parse(nodesJson);
                    if (!Array.isArray(nodes)) {
                        nodes = [];
                    }
                } catch (parseErr) {
                    console.error('解析现有节点失败，将重置节点列表:', parseErr);
                    nodes = [];
                }
            }
            
            // 检查节点ID是否已存在
            if (nodes.some(node => node.id === nodeData.id)) {
                throw new Error(`节点ID '${nodeData.id}' 已存在`);
            }
            
            // 添加新节点
            nodes.push(nodeData);
            
            // 更新JSON并重绘
            $('#workflowNodesJson').val(JSON.stringify(nodes, null, 2));
            
            // 使用防抖动的DAG重绘
            if (window.safeRedrawDAG) {
                window.safeRedrawDAG(true);
            } else {
                console.warn('safeRedrawDAG未定义，使用备用方法');
                if (typeof window.redrawDAG === 'function') {
                    window.redrawDAG(true);
                }
            }
            return true;
        } catch (e) {
            console.error('添加工作流节点失败:', e);
            if (typeof showFeedback === 'function') {
                showFeedback('添加节点失败: ' + e.message, true);
            }
            return false;
        }
    };

    /**
     * 添加新节点到工作流
     * @param {string} nodeType 节点类型 ('start', 'process', 'decision', 'parallel', 'end')
     */
    window.addWorkflowNode = function(nodeType) {
        try {
            // 获取节点数组
            let nodesArray = [];
            try {
                const nodesJson = $('#workflowNodesJson').val().trim();
                if (nodesJson) {
                    nodesArray = JSON.parse(nodesJson);
                }
            } catch (e) {
                console.error('解析节点JSON失败:', e);
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析节点JSON出错'), true);
                return;
            }
            
            // 生成唯一ID
            const nodeId = generateUniqueNodeId(nodeType, nodesArray);
            
            // 创建新节点
            const newNode = {
                nodeId: nodeId,
                nodeName: getDefaultNodeName(nodeType),
                parameters: {}
            };
            
            // 根据节点类型设置特定属性
            switch (nodeType) {
                case 'start':
                    newNode.isStartNode = true;
                    break;
                case 'end':
                    newNode.isEndNode = true;
                    break;
                case 'decision':
                    newNode.isDecisionNode = true;
                    break;
                case 'parallel':
                    newNode.isParallelNode = true;
                    break;
            }
            
            // 添加到数组
            nodesArray.push(newNode);
            
            // 更新JSON
            $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
            
            // 如果图形模式已启用，重绘图表
            if (window.graphModeEnabled) {
                safeRedrawDAG(true);
            }
            
            showFeedback(i18n.translate('tasksPage.feedback.nodeAdded', '节点已添加'), false);
            
            // 打开新节点的编辑对话框
            setTimeout(() => {
                openEditNodeDialog(nodesArray.length - 1);
            }, 100);
        } catch (e) {
            console.error('添加节点失败:', e);
            showFeedback(i18n.translate('tasksPage.feedback.errorAddingNode', '添加节点失败: ') + e.message, true);
        }
    };
    
    /**
     * 生成唯一节点ID
     * @private
     */
    function generateUniqueNodeId(nodeType, nodesArray) {
        const prefix = nodeType.charAt(0).toUpperCase() + nodeType.slice(1);
        let counter = 1;
        let nodeId = `${prefix}${counter}`;
        
        // 确保ID唯一
        const existingIds = new Set(nodesArray.map(node => node.nodeId));
        while (existingIds.has(nodeId)) {
            counter++;
            nodeId = `${prefix}${counter}`;
        }
        
        return nodeId;
    }
    
    /**
     * 获取节点默认名称
     * @private
     */
    function getDefaultNodeName(nodeType) {
        switch (nodeType) {
            case 'start':
                return i18n.translate('tasksPage.nodeTypes.start', '开始节点');
            case 'end':
                return i18n.translate('tasksPage.nodeTypes.end', '结束节点');
            case 'process':
                return i18n.translate('tasksPage.nodeTypes.process', '处理节点');
            case 'decision':
                return i18n.translate('tasksPage.nodeTypes.decision', '决策节点');
            case 'parallel':
                return i18n.translate('tasksPage.nodeTypes.parallel', '并行节点');
            default:
                return i18n.translate('tasksPage.nodeTypes.default', '工作流节点');
        }
    }
    
    window.updateWorkflowNode = function(index, nodeData) {
        try {
            let nodes = [];
            const nodesJson = $('#workflowNodesJson').val();
            if (nodesJson && nodesJson.trim() !== '') {
                nodes = JSON.parse(nodesJson);
            }
            
            if (index >= 0) {
                nodes[index] = nodeData;
            } else {
                nodes.push(nodeData);
            }
            
            $('#workflowNodesJson').val(JSON.stringify(nodes, null, 2));
            window.safeRedrawDAG(true);
            return true;
        } catch (e) {
            console.error('更新工作流节点失败:', e);
            return false;
        }
    };

    /**
     * 删除工作流节点
     * @param {number} nodeIndex 节点在数组中的索引
     */
    window.deleteWorkflowNode = function(nodeIndex) {
        try {
            if (isNaN(nodeIndex)) {
                showFeedback(i18n.translate('tasksPage.feedback.invalidNodeIndex', '无效的节点索引'), true);
                return;
            }
            
            // 获取节点数组
            let nodesArray = [];
            try {
                const nodesJson = $('#workflowNodesJson').val().trim();
                if (nodesJson) {
                    nodesArray = JSON.parse(nodesJson);
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析节点JSON出错'), true);
                return;
            }
            
            if (nodeIndex < 0 || nodeIndex >= nodesArray.length) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeIndexOutOfRange', '节点索引超出范围'), true);
                return;
            }
            
            // 获取要删除的节点ID
            const nodeId = nodesArray[nodeIndex].nodeId;
            
            if (!nodeId) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeIdMissing', '节点ID缺失'), true);
                return;
            }
            
            // 检查边缘中是否引用了此节点
            let edgesArray = [];
            try {
                const edgesJson = $('#workflowEdgesJson').val().trim();
                if (edgesJson) {
                    edgesArray = JSON.parse(edgesJson);
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingEdges', '解析边缘JSON出错'), true);
                return;
            }
            
            const relatedEdges = edgesArray.filter(edge => 
                edge.fromNodeId === nodeId || edge.toNodeId === nodeId
            );
            
            if (relatedEdges.length > 0) {
                if (!confirm(i18n.translate('tasksPage.confirm.deleteNodeWithEdges', 
                    `此节点有 ${relatedEdges.length} 个相关的连接边缘，删除此节点也将删除这些边缘。确定要继续吗？`))) {
                    return;
                }
                
                // 过滤掉相关的边缘
                edgesArray = edgesArray.filter(edge => 
                    edge.fromNodeId !== nodeId && edge.toNodeId !== nodeId
                );
                
                // 更新边缘JSON
                $('#workflowEdgesJson').val(JSON.stringify(edgesArray, null, 2));
            }
            
            // 删除节点
            nodesArray.splice(nodeIndex, 1);
            
            // 更新节点JSON
            $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
            
            // 重绘DAG
            if (window.graphModeEnabled) {
                safeRedrawDAG(true);
            }
            
            showFeedback(i18n.translate('tasksPage.feedback.nodeDeleted', '节点已删除'), false);
        } catch (e) {
            console.error('删除节点失败:', e);
            showFeedback(i18n.translate('tasksPage.feedback.errorDeletingNode', '删除节点失败: ') + e.message, true);
        }
    };

    window.deleteWorkflowNode = function(nodeId) {
        try {
            let nodes = JSON.parse($('#workflowNodesJson').val());
            let edges = [];
            const edgesJson = $('#workflowEdgesJson').val();
            if (edgesJson && edgesJson.trim() !== '') {
                edges = JSON.parse(edgesJson);
            }
            
            nodes = nodes.filter(node => node.id !== nodeId);
            edges = edges.filter(edge => edge.fromNode !== nodeId && edge.toNode !== nodeId);
            
            $('#workflowNodesJson').val(JSON.stringify(nodes, null, 2));
            $('#workflowEdgesJson').val(JSON.stringify(edges, null, 2));
            
            window.safeRedrawDAG(true);
            return true;
        } catch (e) {
            console.error('删除工作流节点失败:', e);
            return false;
        }
    };

    /**
     * 边缘管理函数
     */
    window.addWorkflowEdge = function(edgeData) {
        try {
            let edges = [];
            const edgesJson = $('#workflowEdgesJson').val();
            if (edgesJson && edgesJson.trim() !== '') {
                edges = JSON.parse(edgesJson);
            }
            
            if (edges.some(edge => 
                edge.fromNode === edgeData.fromNode && 
                edge.toNode === edgeData.toNode
            )) {
                throw new Error('边缘连接已存在');
            }
            
            edges.push(edgeData);
            $('#workflowEdgesJson').val(JSON.stringify(edges, null, 2));
            window.safeRedrawDAG(true);
            return true;
        } catch (e) {
            console.error('添加工作流边缘失败:', e);
            return false;
        }
    };

    window.updateWorkflowEdge = function(index, edgeData) {
        try {
            let edges = JSON.parse($('#workflowEdgesJson').val());
            
            if (index < 0 || index >= edges.length) {
                throw new Error('无效的边缘索引');
            }
            
            edges[index] = edgeData;
            $('#workflowEdgesJson').val(JSON.stringify(edges, null, 2));
            window.safeRedrawDAG(true);
            return true;
        } catch (e) {
            console.error('更新工作流边缘失败:', e);
            return false;
        }
    };

    /**
     * 打开节点编辑对话框
     * @param {number} nodeIndex 节点在数组中的索引
     */
    window.openEditNodeDialog = function(nodeIndex) {
        try {
            console.log(`打开节点编辑对话框，索引: ${nodeIndex}`);
            
            // 获取节点数据
            let nodesArray = [];
            try {
                const nodesJson = $('#workflowNodesJson').val();
                if (nodesJson) {
                    nodesArray = JSON.parse(nodesJson);
                }
            } catch (e) {
                console.error('解析节点JSON时出错:', e);
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析节点JSON时出错'), true);
                return;
            }
            
            if (nodeIndex >= nodesArray.length) {
                console.error(`节点索引超出范围: ${nodeIndex}, 节点数组长度: ${nodesArray.length}`);
                return;
            }
            
            const node = nodesArray[nodeIndex];
            
            // 存储当前编辑的节点索引，用于保存时更新数组的正确位置
            $('#editingNodeArrayIndex').val(nodeIndex);
            
            // 填充表单字段
            $('#displayNodeId').text('Node ID: ' + node.nodeId);
            $('#wfNodeId').val(node.nodeId);
            $('#wfNodeName').val(node.nodeName || '');
            
            // 填充参数
            if (node.parameters) {
                try {
                    $('#wfNodeParams').val(JSON.stringify(node.parameters, null, 2));
                } catch (e) {
                    console.warn('无法格式化节点参数:', e);
                    $('#wfNodeParams').val(JSON.stringify(node.parameters));
                }
            } else {
                $('#wfNodeParams').val('{}');
            }
            
            // 在展示模态框前确保先加载任务列表
            if (typeof window.loadAvailableTasksForNodes === 'function') {
                console.log('预加载任务选择列表');
                window.loadAvailableTasksForNodes();
                
                // 延迟一点时间设置任务ID，确保任务列表已加载
                setTimeout(function() {
                    // 设置任务ID（如果有）
                    if (node.taskConfigId) {
                        $('#availableTasksForNodes').val(node.taskConfigId).trigger('change');
                        
                        // 确保在所有可能的任务选择框上同步选择
                        const taskSelects = $('.modal select[id*="task"], .modal select[id*="Task"], .modal select.task-select');
                        if (taskSelects.length > 0) {
                            taskSelects.val(node.taskConfigId).trigger('change');
                        }
                    } else {
                        $('#availableTasksForNodes').val('').trigger('change');
                    }
                }, 200);
            } else {
                // 直接设置任务ID
                if (node.taskConfigId) {
                    $('#availableTasksForNodes').val(node.taskConfigId).trigger('change');
                } else {
                    $('#availableTasksForNodes').val('').trigger('change');
                }
            }
            
            // 打开模态窗口
            $('#workflowNodeEditModal').modal('show');
            
            console.log(`已打开节点编辑对话框 - 索引: ${nodeIndex}, ID: ${node.nodeId}`);
        } catch (e) {
            console.error('打开节点编辑对话框时出错:', e);
            showFeedback(i18n.translate('tasksPage.feedback.errorOpeningNodeDialog', '打开节点编辑对话框失败: ') + e.message, true);
        }
    };

    /**
     * 保存工作流节点编辑
     */
    window.saveWorkflowNode = function() {
        try {
            // 获取编辑的节点索引
            const nodeIndex = parseInt($('#editingNodeArrayIndex').val());
            if (isNaN(nodeIndex) && nodeIndex !== 0) {
                throw new Error(i18n.translate('tasksPage.feedback.invalidNodeIndex', '无效的节点索引'));
            }
            
            // 获取表单数据
            const nodeId = $('#wfNodeId').val().trim();
            const nodeName = $('#wfNodeName').val().trim();
            
            // 获取任务ID - 增强逻辑以从多个可能的选择器中获取
            let taskConfigId = '';
            // 尝试从主选择器获取
            taskConfigId = $('#availableTasksForNodes').val();
            
            // 如果主选择器没有值，尝试从其他可能的选择器获取
            if (!taskConfigId) {
                const possibleSelectors = [
                    '#taskConfig', 
                    '#nodeTaskConfig', 
                    '#selectTask',
                    '#wfTaskSelect',
                    'select[id*="task"]',
                    'select[id*="Task"]',
                    'select.task-select',
                    '.modal:visible select' // 所有可见模态框中的选择框
                ];
                
                for (let selector of possibleSelectors) {
                    const $select = $(selector);
                    if ($select.length > 0) {
                        const value = $select.val();
                        if (value) {
                            taskConfigId = value;
                            console.log(`从选择器 ${selector} 获取到任务ID: ${value}`);
                            break;
                        }
                    }
                }
            }
            
            console.log('最终获取的任务ID:', taskConfigId || '未选择任务');
            
            if (!nodeId) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeIdRequired', '节点ID不能为空'), true);
                return;
            }
            
            // 解析参数JSON
            let parameters = {};
            try {
                const paramsStr = $('#wfNodeParams').val().trim();
                if (paramsStr) {
                    parameters = JSON.parse(paramsStr);
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.invalidNodeParams', '节点参数JSON格式无效'), true);
                return;
            }
            
            // 获取当前节点数组
            let nodesArray = [];
            try {
                const nodesJson = $('#workflowNodesJson').val().trim();
                if (nodesJson) {
                    nodesArray = JSON.parse(nodesJson);
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析节点JSON时出错'), true);
                return;
            }
            
            // 更新或创建节点
            const updatedNode = {
                nodeId: nodeId,
                nodeName: nodeName,
                parameters: parameters
            };
            
            // 如果选择了任务，则添加taskConfigId
            if (taskConfigId) {
                updatedNode.taskConfigId = taskConfigId;
            }
            
            // 保留位置信息（如果有）
            if (nodesArray[nodeIndex] && nodesArray[nodeIndex].position) {
                updatedNode.position = nodesArray[nodeIndex].position;
            }
            
            // 更新节点
            nodesArray[nodeIndex] = updatedNode;
            
            // 更新JSON
            $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
            
            // 重绘DAG
            if (window.graphModeEnabled) {
                safeRedrawDAG(true);
            }
            
            // 关闭模态框
            $('#workflowNodeEditModal').modal('hide');
            
            showFeedback(i18n.translate('tasksPage.feedback.nodeSaved', '节点已保存'), false);
        } catch (e) {
            console.error('保存节点时出错:', e);
            showFeedback(i18n.translate('tasksPage.feedback.errorSavingNode', '保存节点失败: ') + e.message, true);
        }
    };

    /**
     * 打开边缘编辑对话框
     * @param {number} edgeIndex 边缘在数组中的索引
     */
    window.openEdgeEditDialog = function(edgeIndex) {
        try {
            // 获取边缘数组
            let edgesArray = [];
            try {
                const edgesJson = $('#workflowEdgesJson').val();
                edgesArray = edgesJson && edgesJson.trim() !== '' ? JSON.parse(edgesJson) : [];
            } catch (e) {
                console.error('解析边缘JSON时出错:', e);
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingEdges', '解析边缘JSON时出错'), true);
                return;
            }
            
            // 获取节点数组（用于填充下拉菜单）
            let nodesArray = [];
            try {
                const nodesJson = $('#workflowNodesJson').val();
                nodesArray = nodesJson && nodesJson.trim() !== '' ? JSON.parse(nodesJson) : [];
            } catch (e) {
                console.error('解析节点JSON时出错:', e);
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析节点JSON时出错'), true);
                return;
            }
            
            // 清空并填充节点下拉菜单
            const fromSelect = $('#wfEdgeFrom');
            const toSelect = $('#wfEdgeTo');
            fromSelect.empty();
            toSelect.empty();
            
            // 添加空选项
            fromSelect.append($('<option>').val('').text('-- ' + i18n.translate('tasksPage.edgeModal.selectNode', '选择节点') + ' --'));
            toSelect.append($('<option>').val('').text('-- ' + i18n.translate('tasksPage.edgeModal.selectNode', '选择节点') + ' --'));
            
            // 添加所有节点
            for (const node of nodesArray) {
                const optionText = `${node.nodeId}${node.nodeName ? ' - ' + node.nodeName : ''}`;
                fromSelect.append($('<option>').val(node.nodeId).text(optionText));
                toSelect.append($('<option>').val(node.nodeId).text(optionText));
            }
            
            // 存储当前边缘索引
            $('#editingEdgeArrayIndex').val(edgeIndex !== undefined ? edgeIndex : -1);
            
            // 清空表单
            $('#wfEdgeExpression').val('');
            $('#wfEdgePriority').val(1);
            
            if (edgeIndex !== undefined && edgeIndex >= 0 && edgeIndex < edgesArray.length) {
                // 编辑现有的边缘
                const edge = edgesArray[edgeIndex];
                $('#wfEdgeFrom').val(edge.fromNodeId);
                $('#wfEdgeTo').val(edge.toNodeId);
                $('#wfEdgeExpression').val(edge.expression || '');
                $('#wfEdgePriority').val(edge.priority || 1);
                
                // 显示删除按钮
                $('#deleteEdgeBtn').show();
            } else {
                // 创建新边缘
                $('#deleteEdgeBtn').hide();
            }
            
            // 显示模态框
            $('#workflowEdgeEditModal').modal('show');
        } catch (e) {
            console.error('打开边缘编辑对话框时出错:', e);
            showFeedback(i18n.translate('tasksPage.feedback.errorOpeningEdgeDialog', '打开边缘编辑对话框失败: ') + e.message, true);
        }
    };

    /**
     * 保存工作流边缘
     */
    window.saveWorkflowEdge = function() {
        try {
            // 获取表单数据
            const fromNodeId = $('#wfEdgeFrom').val();
            const toNodeId = $('#wfEdgeTo').val();
            const expression = $('#wfEdgeExpression').val().trim();
            const priority = parseInt($('#wfEdgePriority').val()) || 1;
            
            if (!fromNodeId || !toNodeId) {
                showFeedback(i18n.translate('tasksPage.feedback.selectFromToNodes', '请选择源节点和目标节点'), true);
                return;
            }
            
            if (fromNodeId === toNodeId) {
                showFeedback(i18n.translate('tasksPage.feedback.noSelfLoops', '不能创建自循环边缘（源节点和目标节点相同）'), true);
                return;
            }
            
            // 获取当前边缘数组
            let edgesArray = [];
            try {
                const edgesJson = $('#workflowEdgesJson').val().trim();
                if (edgesJson) {
                    edgesArray = JSON.parse(edgesJson);
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingEdges', '解析边缘JSON时出错'), true);
                return;
            }
            
            // 获取当前编辑的边缘索引
            const edgeIndex = parseInt($('#editingEdgeArrayIndex').val());
            
            // 创建或更新边缘对象
            const edge = {
                fromNodeId: fromNodeId,
                toNodeId: toNodeId
            };
            
            // 仅当有表达式时添加表达式字段
            if (expression) {
                edge.expression = expression;
            }
            
            // 添加优先级
            edge.priority = priority;
            
            // 检查是否已存在相同的 fromNodeId-toNodeId 组合（除了当前编辑的边缘）
            const duplicateIndex = edgesArray.findIndex((e, index) => 
                e.fromNodeId === fromNodeId && 
                e.toNodeId === toNodeId && 
                (isNaN(edgeIndex) || index !== edgeIndex)
            );
            
            if (duplicateIndex !== -1) {
                showFeedback(i18n.translate('tasksPage.feedback.duplicateEdge', '已存在从这两个节点之间的连接'), true);
                return;
            }
            
            // 更新或添加边缘
            if (!isNaN(edgeIndex) && edgeIndex >= 0 && edgeIndex < edgesArray.length) {
                // 更新现有边缘
                edgesArray[edgeIndex] = edge;
            } else {
                // 添加新边缘
                edgesArray.push(edge);
            }
            
            // 更新JSON
            $('#workflowEdgesJson').val(JSON.stringify(edgesArray, null, 2));
            
            // 重绘DAG
            if (window.graphModeEnabled) {
                safeRedrawDAG(true);
            }
            
            // 关闭模态框
            $('#workflowEdgeEditModal').modal('hide');
            
            showFeedback(i18n.translate('tasksPage.feedback.edgeSaved', '边缘已保存'), false);
        } catch (e) {
            console.error('保存边缘时出错:', e);
            showFeedback(i18n.translate('tasksPage.feedback.errorSavingEdge', '保存边缘失败: ') + e.message, true);
        }
    };

    /**
     * 删除工作流边缘
     */
    window.deleteWorkflowEdge = function() {
        try {
            // 获取当前编辑的边缘索引
            const edgeIndex = parseInt($('#editingEdgeArrayIndex').val());
            
            if (isNaN(edgeIndex) || edgeIndex < 0) {
                showFeedback(i18n.translate('tasksPage.feedback.noEdgeSelected', '没有选择要删除的边缘'), true);
                return;
            }
            
            // 获取当前边缘数组
            let edgesArray = [];
            try {
                const edgesJson = $('#workflowEdgesJson').val().trim();
                if (edgesJson) {
                    edgesArray = JSON.parse(edgesJson);
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingEdges', '解析边缘JSON时出错'), true);
                return;
            }
            
            if (edgeIndex >= edgesArray.length) {
                showFeedback(i18n.translate('tasksPage.feedback.edgeIndexOutOfRange', '边缘索引超出范围'), true);
                return;
            }
            
            // 删除边缘
            edgesArray.splice(edgeIndex, 1);
            
            // 更新JSON
            $('#workflowEdgesJson').val(JSON.stringify(edgesArray, null, 2));
            
            // 重绘DAG
            if (window.graphModeEnabled) {
                safeRedrawDAG(true);
            }
            
            // 关闭模态框
            $('#workflowEdgeEditModal').modal('hide');
            
            showFeedback(i18n.translate('tasksPage.feedback.edgeDeleted', '边缘已删除'), false);
        } catch (e) {
            console.error('删除边缘时出错:', e);
            showFeedback(i18n.translate('tasksPage.feedback.errorDeletingEdge', '删除边缘失败: ') + e.message, true);
        }
    };
    
    /**
     * 工作流验证函数
     */
    window.validateWorkflow = function() {
        try {
            const nodes = JSON.parse($('#workflowNodesJson').val() || '[]');
            const edges = JSON.parse($('#workflowEdgesJson').val() || '[]');
            
            if (nodes.length === 0) {
                throw new Error('工作流必须至少包含一个节点');
            }
            
            const nodeIds = new Set();
            for (const node of nodes) {
                if (nodeIds.has(node.id)) {
                    throw new Error(`存在重复的节点ID: ${node.id}`);
                }
                nodeIds.add(node.id);
            }
            
            for (const edge of edges) {
                if (!nodeIds.has(edge.fromNode)) {
                    throw new Error(`边缘引用了不存在的源节点: ${edge.fromNode}`);
                }
                if (!nodeIds.has(edge.toNode)) {
                    throw new Error(`边缘引用了不存在的目标节点: ${edge.toNode}`);
                }
            }
            
            return true;
        } catch (e) {
            console.error('工作流验证失败:', e);
            return false;
        }
    };

    /**
     * 确保工作流节点位置变更被保存到JSON中
     * 在表单提交前调用，以确保视图中的变更被应用到JSON数据
     */
    window.ensureWorkflowChangesSaved = function() {
        try {
            if (!window.nodePositionChanged) {
                // 如果没有位置变更，不需要更新
                return true;
            }
            
            console.log('检测到节点位置变更，正在同步到JSON数据...');
            
            // 获取节点数据
            let nodesArray = [];
            try {
                const nodesJson = $('#workflowNodesJson').val().trim();
                if (nodesJson) {
                    nodesArray = JSON.parse(nodesJson);
                }
            } catch (e) {
                console.error('解析节点JSON失败:', e);
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析节点JSON出错'), true);
                return false;
            }
            
            // 更新每个节点的位置信息
            nodesArray.forEach((node, index) => {
                const nodeId = node.nodeId;
                if (window.nodePositionsMap[nodeId]) {
                    // 复制位置信息
                    node.position = {
                        x: window.nodePositionsMap[nodeId].x,
                        y: window.nodePositionsMap[nodeId].y
                    };
                }
            });
            
            // 更新JSON
            $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
            
            // 重置位置变更标志
            window.nodePositionChanged = false;
            
            return true;
        } catch (e) {
            console.error('保存节点位置失败:', e);
            showFeedback(i18n.translate('tasksPage.feedback.errorSavingNodePositions', '保存节点位置失败: ') + e.message, true);
            return false;
        }
    };

    /**
     * 检查工作流的安全状态
     * 在保存工作流前调用，确保节点和边缘数据有效
     */
    window.checkWorkflowSafetyState = function() {
        try {
            // 检查节点数据
            let nodesArray = [];
            try {
                const nodesJson = $('#workflowNodesJson').val().trim();
                if (nodesJson) {
                    nodesArray = JSON.parse(nodesJson);
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.invalidNodesJson', '节点JSON格式无效'), true);
                return false;
            }
            
            if (!Array.isArray(nodesArray) || nodesArray.length === 0) {
                console.warn('工作流必须包含至少一个节点');
                return true; // 不阻止保存，但记录警告
            }
            
            // 检查节点ID的唯一性
            const nodeIds = new Set();
            for (const node of nodesArray) {
                if (!node.nodeId) {
                    console.warn('发现未命名节点');
                    continue;
                }
                
                if (nodeIds.has(node.nodeId)) {
                    showFeedback(i18n.translate('tasksPage.feedback.duplicateNodeId', '存在重复的节点ID: ') + node.nodeId, true);
                    return false;
                }
                
                nodeIds.add(node.nodeId);
            }
            
            // 检查边缘数据
            let edgesArray = [];
            try {
                const edgesJson = $('#workflowEdgesJson').val().trim();
                if (edgesJson) {
                    edgesArray = JSON.parse(edgesJson);
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.invalidEdgesJson', '边缘JSON格式无效'), true);
                return false;
            }
            
            if (Array.isArray(edgesArray) && edgesArray.length > 0) {
                // 检查边缘引用的节点是否存在
                for (const edge of edgesArray) {
                    if (!edge.fromNodeId || !edge.toNodeId) {
                        showFeedback(i18n.translate('tasksPage.feedback.incompleteEdge', '边缘必须指定源节点和目标节点'), true);
                        return false;
                    }
                    
                    if (!nodeIds.has(edge.fromNodeId)) {
                        showFeedback(i18n.translate('tasksPage.feedback.edgeSourceNotFound', '边缘引用的源节点不存在: ') + edge.fromNodeId, true);
                        return false;
                    }
                    
                    if (!nodeIds.has(edge.toNodeId)) {
                        showFeedback(i18n.translate('tasksPage.feedback.edgeTargetNotFound', '边缘引用的目标节点不存在: ') + edge.toNodeId, true);
                        return false;
                    }
                    
                    if (edge.fromNodeId === edge.toNodeId) {
                        console.warn('发现自循环边缘:', edge);
                    }
                }
            } else {
                console.warn('工作流没有边缘连接节点');
            }
            
            return true;
        } catch (e) {
            console.error('检查工作流安全状态时出错:', e);
            showFeedback(i18n.translate('tasksPage.feedback.errorCheckingWorkflow', '检查工作流安全状态时出错: ') + e.message, true);
            return false;
        }
    };
    
    /**
     * 事件处理函数初始化
     */
    $(document).ready(function() {
        // 添加节点按钮点击事件
        $('#addNodeBtn').on('click', function() {
            const nodeId = 'node_' + Date.now();
            const nodeData = {
                id: nodeId,
                name: '新节点',
                parameters: {}
            };
            window.addWorkflowNode(nodeData);
        });

        // 保存节点更改按钮点击事件
        $('#saveNodeChangesBtn').on('click', function() {
            const index = parseInt($('#editingNodeArrayIndex').val());
            const nodeData = {
                id: $('#wfNodeId').val(),
                name: $('#wfNodeName').val(),
                taskConfigId: $('#availableTasksForNodes').val(),
                parameters: JSON.parse($('#wfNodeParams').val() || '{}')
            };
            
            if (window.updateWorkflowNode(index, nodeData)) {
                $('#workflowNodeEditModal').modal('hide');
            }
        });

        // 保存边缘按钮点击事件
        $('#saveEdgeBtn').on('click', function() {
            const index = parseInt($('#editingEdgeIndex').val());
            const edgeData = {
                fromNode: $('#editEdgeFromNode').val(),
                toNode: $('#editEdgeToNode').val(),
                expression: $('#editEdgeExpression').val(),
                priority: parseInt($('#editEdgePriority').val() || '0')
            };
            
            if (window.updateWorkflowEdge(index, edgeData)) {
                $('#workflowEdgeEditModal').modal('hide');
            }
        });

        // 删除边缘按钮点击事件
        $('#deleteEdgeBtn').on('click', function() {
            const fromNode = $('#editEdgeFromNode').val();
            const toNode = $('#editEdgeToNode').val();
            
            if (window.deleteWorkflowEdge(fromNode, toNode)) {
                $('#workflowEdgeEditModal').modal('hide');
            }
        });

        // 应用更改按钮点击事件
        $('#applyChangesBtn').on('click', function() {
            if (window.validateWorkflow()) {
                $('#task-form').submit();
            }
        });

        // 缩放控制按钮点击事件
        $('#zoomInBtn').on('click', function() {
            const svg = $('#dagContainer svg');
            if (svg.length) {
                const viewBox = svg.attr('viewBox').split(' ').map(parseFloat);
                const newWidth = viewBox[2] * 0.8;
                const newHeight = viewBox[3] * 0.8;
                svg.attr('viewBox', `${viewBox[0]} ${viewBox[1]} ${newWidth} ${newHeight}`);
            }
        });

        $('#zoomOutBtn').on('click', function() {
            const svg = $('#dagContainer svg');
            if (svg.length) {
                const viewBox = svg.attr('viewBox').split(' ').map(parseFloat);
                const newWidth = viewBox[2] * 1.2;
                const newHeight = viewBox[3] * 1.2;
                svg.attr('viewBox', `${viewBox[0]} ${viewBox[1]} ${newWidth} ${newHeight}`);
            }
        });

        $('#resetZoomBtn').on('click', function() {
            const svg = $('#dagContainer svg');
            if (svg.length) {
                svg.attr('viewBox', '0 0 1200 800');
            }
        });

        // 图形模式切换
        $('#editorModeGraph').on('click', function() {
            $(this).addClass('active');
            $('#editorModeForm').removeClass('active');
            $('#graphModeControls').show();
            $('#formModeControls').hide();
            window.graphModeEnabled = true;
            window.svgDraggable = true;
            window.safeRedrawDAG(true);
        });

        $('#editorModeForm').on('click', function() {
            $(this).addClass('active');
            $('#editorModeGraph').removeClass('active');
            $('#graphModeControls').hide();
            $('#formModeControls').show();
            window.graphModeEnabled = false;
            window.svgDraggable = false;
            window.safeRedrawDAG(false);
        });

        // 快速添加节点下拉菜单项点击事件
        $('.quick-add-node').on('click', function(e) {
            e.preventDefault();
            const nodeType = $(this).data('node-type');
            const nodeId = `${nodeType}_${Date.now()}`;
            
            const nodeData = {
                id: nodeId,
                name: nodeType.charAt(0).toUpperCase() + nodeType.slice(1),
                parameters: {
                    [`is${nodeType.charAt(0).toUpperCase() + nodeType.slice(1)}Node`]: true
                }
            };
            
            window.addWorkflowNode(nodeData);
        });
    });

    /**
     * 测试函数 - 仅用于开发环境
     */
    window.testWorkflowFunctionality = function() {
        try {
            console.log('开始测试工作流功能...');
            
            // 测试节点操作
            const testNode1 = {
                id: 'test_start',
                name: 'Start Node',
                parameters: { isStartNode: true }
            };
            
            const testNode2 = {
                id: 'test_process',
                name: 'Process Node',
                taskConfigId: '1',
                parameters: {}
            };
            
            console.log('测试添加节点...');
            if (!window.addWorkflowNode(testNode1)) {
                throw new Error('添加起始节点失败');
            }
            
            if (!window.addWorkflowNode(testNode2)) {
                throw new Error('添加处理节点失败');
            }
            
            console.log('测试更新节点...');
            testNode2.name = 'Updated Process';
            if (!window.updateWorkflowNode(1, testNode2)) {
                throw new Error('更新节点失败');
            }
            
            // 测试边缘操作
            console.log('测试添加边缘...');
            const testEdge = {
                fromNode: 'test_start',
                toNode: 'test_process',
                expression: '${test_start_status} == "SUCCESS"',
                priority: 1
            };
            
            if (!window.addWorkflowEdge(testEdge)) {
                throw new Error('添加边缘失败');
            }
            
            // 测试工作流验证
            console.log('测试工作流验证...');
            if (!window.validateWorkflow()) {
                throw new Error('工作流验证失败');
            }
            
            console.log('基础功能测试完成');
            return true;
        } catch (e) {
            console.error('测试失败:', e);
            return false;
        }
    };

    console.log('tasks workflow脚本初始化完成');
})();
