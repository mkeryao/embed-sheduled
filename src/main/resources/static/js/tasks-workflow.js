/**
 * tasks-workflow.js
 * 工作流程图相关功能，包含DAG操作、节点和边的管理
 */

// 避免重复初始化
if (typeof window.tasksWorkflowInitialized === 'undefined') {
    console.log('初始化tasks workflow脚本...');
    window.tasksWorkflowInitialized = true;
    
    // 图形模式变量
    window.graphModeEnabled = false;
    window.currentScale = 1.0;
    window.svgDraggable = false;
    window.nodePositionChanged = false;
    window.nodeDragging = false;
    window.currentDragNode = null;
    window.nodePositionsMap = {};
    
    // 添加DAG操作指南功能
    window.showDagOperationGuide = function() {
        const guideContainer = $('<div>')
            .addClass('dag-operation-guide')
            .css({
                position: 'absolute',
                top: '5px',
                right: '5px',
                background: 'rgba(255, 255, 255, 0.95)',
                padding: '10px',
                borderRadius: '5px',
                border: '1px solid #ddd',
                boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
                fontSize: '0.85em',
                maxWidth: '250px',
                zIndex: 100
            });

        const guideTitle = $('<h6>').text(i18n.translate('tasksPage.workflowGuide.title'));
        const guideList = $('<ul>').css({
            'padding-left': '20px',
            'margin-bottom': '5px'
        });

        const guideItems = [
            i18n.translate('tasksPage.workflowGuide.item1'),
            i18n.translate('tasksPage.workflowGuide.item2'),
            i18n.translate('tasksPage.workflowGuide.item3'),
            i18n.translate('tasksPage.workflowGuide.item4')
        ];

        guideItems.forEach(item => {
            guideList.append($('<li>').text(item));
        });

        const closeBtn = $('<button>')
            .addClass('close')
            .html('&times;')
            .css({
                position: 'absolute',
                top: '5px',
                right: '8px',
                fontSize: '1.2em',
                padding: '0',
                margin: '0',
                background: 'none',
                border: 'none',
                cursor: 'pointer'
            })
            .attr('title', i18n.translate('tasksPage.workflowGuide.closeTitle', '关闭指南'))
            .on('click', function() {
                guideContainer.remove();
            });

        guideContainer.append(closeBtn);
        guideContainer.append(guideTitle);
        guideContainer.append(guideList);

        return guideContainer;
    };

    // 检测工作流中是否存在循环
    window.detectCycleInWorkflow = function(nodes, edges) {
        if (!nodes || nodes.length === 0 || !edges || edges.length === 0) {
            return false;
        }

        const adj = new Map();
        const nodeIds = new Set();

        nodes.forEach(node => {
            if (node && node.nodeId) {
                nodeIds.add(node.nodeId);
                adj.set(node.nodeId, []);
            }
        });

        if (nodeIds.size === 0) return false;

        edges.forEach(edge => {
            if (edge && edge.fromNodeId && edge.toNodeId &&
                nodeIds.has(edge.fromNodeId) && nodeIds.has(edge.toNodeId)) {
                adj.get(edge.fromNodeId).push(edge.toNodeId);
            }
        });

        const visitStatus = new Map();
        nodeIds.forEach(nodeId => visitStatus.set(nodeId, 0));

        function dfs(currentNodeId) {
            visitStatus.set(currentNodeId, 1);

            const neighbors = adj.get(currentNodeId) || [];
            for (const neighborId of neighbors) {
                if (!visitStatus.has(neighborId)) continue;

                if (visitStatus.get(neighborId) === 1) {
                    return true;
                }
                if (visitStatus.get(neighborId) === 0) {
                    if (dfs(neighborId)) {
                        return true;
                    }
                }
            }
            visitStatus.set(currentNodeId, 2);
            return false;
        }

        for (const nodeId of nodeIds) {
            if (visitStatus.get(nodeId) === 0) {
                if (dfs(nodeId)) {
                    return true;
                }
            }
        }
        return false;
    };

    // 重绘DAG图
    window.redrawDAG = function(enableDragging) {
        const svgContainer = $('#dagContainer');
        svgContainer.empty();

        // 移除之前的操作指南
        svgContainer.find('.dag-operation-guide').remove();
        
        // 确保在重绘DAG时节点助手保持可见
        if (window.graphModeEnabled && typeof window.ensureNodeHelperVisible === 'function') {
            window.ensureNodeHelperVisible();
        }
        
        // 进行安全检查
        if (typeof window.checkWorkflowSafetyState === 'function') {
            window.checkWorkflowSafetyState();
        }

        if (window.selectedSourceElement) {
            window.selectedSourceElement.find('rect').attr('fill', '#fff');
        }
        window.selectedSourceNodeId = null;
        window.selectedSourceElement = null;

        let nodesJson = $('#workflowNodesJson').val();
        let nodesArray = [];

        if (!nodesJson || nodesJson.trim() === '') {
            svgContainer.append(`<p class="text-muted small">${i18n.translate('tasksPage.modal.workflowTaskFields.dagContainerPlaceholderNoNodes')}</p>`);
            return;
        }

        try {
            nodesArray = JSON.parse(nodesJson);
            if (!Array.isArray(nodesArray)) {
                svgContainer.append(`<p class="text-danger small">${i18n.translate('tasksPage.modal.workflowTaskFields.dagContainerErrorNotArray')}</p>`);
                return;
            }
        } catch (e) {
            svgContainer.append(`<p class="text-danger small">${i18n.translate('tasksPage.modal.workflowTaskFields.dagContainerErrorParseNodes')}: ${e.message}</p>`);
            return;
        }

        if (nodesArray.length === 0) {
            svgContainer.append(`<p class="text-muted small">${i18n.translate('tasksPage.modal.workflowTaskFields.dagContainerPlaceholderNoNodes')}</p>`);
            return;
        }

        const svgNS = 'http://www.w3.org/2000/svg';
        const svg = $(document.createElementNS(svgNS, 'svg'));
        svg.attr('width', '100%');

        if (svg.find('#arrowhead').length === 0) {
            const defs = $(document.createElementNS(svgNS, 'defs'));
            const marker = $(document.createElementNS(svgNS, 'marker'));
            marker.attr('id', 'arrowhead').attr('viewBox', '-0 -5 10 10').attr('refX', 8).attr('refY', 0)
                .attr('orient', 'auto').attr('markerWidth', 6).attr('markerHeight', 6).attr('overflow', 'visible');
            marker.append($(document.createElementNS(svgNS, 'path')).attr('d', 'M 0,-5 L 10 ,0 L 0,5').attr('fill', '#333'));
            defs.append(marker);
            svg.prepend(defs);
        }

        const nodeWidth = 150;
        const nodeHeight = 60;
        const nodeVMargin = 20;
        const nodeHMargin = 20;
        const startX = nodeHMargin;
        let currentY = nodeVMargin;

        nodesArray.forEach(function (node, index) {
            const g = $(document.createElementNS(svgNS, 'g'));
            const nodeCenterX = startX + nodeWidth / 2;
            const nodeCenterY = currentY + nodeHeight / 2;

            g.attr('transform', `translate(${startX}, ${currentY})`);
            g.attr('class', 'dag-node node-group');
            g.addClass('dag-node');  // 添加类而不仅是设置属性，确保CSS选择器能匹配
            g.attr('data-node-id', node.nodeId);
            g.data('nodeCenter', {x: nodeCenterX, y: nodeCenterY});

            const rect = $(document.createElementNS(svgNS, 'rect'));
            rect.attr('x', 0).attr('y', 0)
                .attr('width', nodeWidth).attr('height', nodeHeight)
                .attr('fill', '#fff').attr('stroke', '#007bff')
                .attr('stroke-width', 2).attr('rx', 5);
            g.append(rect);

            // 添加鼠标按下事件处理（用于拖拽）
            rect.on('mousedown', function(event) {
                if (window.graphModeEnabled) {
                    event.preventDefault();
                    event.stopPropagation();
                    
                    const parentGroup = $(this).closest('g.node-group');
                    window.nodeDragging = true;
                    window.currentDragNode = parentGroup;
                    
                    // 改变鼠标指针
                    $('body').css('cursor', 'move');
                    
                    // 高亮节点以表示它被选中
                    $(this).attr({
                        'stroke': '#28a745',
                        'stroke-width': 3,
                        'fill': '#f0fff0'
                    });
                }
            });
            
            rect.on('click', function (event) {
                event.stopPropagation();
                
                // 如果是在拖拽模式下，忽略点击
                if (window.nodeDragging) {
                    return;
                }
                
                const parentGroup = $(this).closest('g.node-group');
                const clickedNodeId = parentGroup.attr('data-node-id');
                
                // 显示临时提示信息
                function showNodeActionStatus(message, isSource) {
                    const statusDiv = $('<div>')
                        .addClass('node-action-status')
                        .css({
                            position: 'absolute',
                            top: '10px',
                            left: '50%',
                            transform: 'translateX(-50%)',
                            padding: '5px 10px',
                            background: isSource ? '#d4edda' : '#cce5ff',
                            color: isSource ? '#155724' : '#004085',
                            borderRadius: '3px',
                            boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
                            zIndex: 1000,
                            fontSize: '0.9em'
                        })
                        .text(message);
                    
                    $('#dagContainer').append(statusDiv);
                    
                    // 3秒后自动淡出
                    setTimeout(() => {
                        statusDiv.fadeOut(500, function() {
                            $(this).remove();
                        });
                    }, 3000);
                }

                if (!window.selectedSourceNodeId) {
                    // 选择源节点
                    window.selectedSourceNodeId = clickedNodeId;
                    window.selectedSourceElement = parentGroup;
                    
                    // 使用增强的节点选择反馈
                    window.enhanceNodeSelection(parentGroup, clickedNodeId);
                    
                } else {
                    if (window.selectedSourceNodeId === clickedNodeId) {
                        // 用户点击了相同节点 - 取消选择
                        if (window.selectedSourceElement) {
                            window.selectedSourceElement.find('rect')
                                .attr('fill', '#fff')
                                .attr('stroke', '#007bff')
                                .attr('stroke-width', 2)
                                .css('filter', 'none');
                        }
                        window.selectedSourceNodeId = null;
                        window.selectedSourceElement = null;
                        
                        // 移除边缘创建指引
                        window.removeEdgeVisualGuide();
                        
                        // 显示取消选择的反馈
                        window.showFloatingInfo('已取消选择', '取消创建边缘连接', 'warning');
                        
                        // 移除任何状态消息
                        $('#dagContainer').find('.node-action-status').remove();
                    } else {
                        // 用户选择了目标节点 - 打开边缘属性对话框
                        // 临时高亮目标节点
                        const originalFill = parentGroup.find('rect').attr('fill');
                        const originalStroke = parentGroup.find('rect').attr('stroke');
                        const originalStrokeWidth = parentGroup.find('rect').attr('stroke-width');
                        
                        // 移除边缘创建指引
                        window.removeEdgeVisualGuide();
                        
                        // 更高级的目标节点高亮效果
                        parentGroup.find('rect')
                            .attr('fill', '#d4edda')
                            .attr('stroke', '#28a745')
                            .attr('stroke-width', 3)
                            .css('filter', 'drop-shadow(0 0 4px rgba(40, 167, 69, 0.5))');
                        
                        // 创建表示边缘的临时动画SVG线条
                        let sourcePos = null;
                        if (window.selectedSourceElement) {
                            sourcePos = window.selectedSourceElement.data('nodeCenter');
                        }
                        const targetPos = parentGroup.data('nodeCenter');
                        
                        if (sourcePos && targetPos) {
                            const svgNS = 'http://www.w3.org/2000/svg';
                            const tempEdge = $(document.createElementNS(svgNS, 'path'))
                                .attr({
                                    'id': 'tempAnimatedEdge',
                                    'stroke': '#28a745',
                                    'stroke-width': 2,
                                    'fill': 'none',
                                    'stroke-dasharray': '5,5',
                                    'marker-end': 'url(#arrowhead)'
                                })
                                .css({
                                    'animation': 'dashAnimation 1s linear infinite',
                                    'pointer-events': 'none'
                                });
                            
                            // 创建平滑的贝塞尔曲线路径
                            const dx = targetPos.x - sourcePos.x;
                            const dy = targetPos.y - sourcePos.y;
                            const controlX1 = sourcePos.x + dx * 0.4;
                            const controlY1 = sourcePos.y;
                            const controlX2 = targetPos.x - dx * 0.4;
                            const controlY2 = targetPos.y;
                            
                            const pathData = `M${sourcePos.x},${sourcePos.y} C${controlX1},${controlY1} ${controlX2},${controlY2} ${targetPos.x},${targetPos.y}`;
                            tempEdge.attr('d', pathData);
                            
                            // 添加到SVG
                            $('#dagContainer svg').append(tempEdge);
                            
                            // 添加CSS动画
                            const styleElement = document.createElement('style');
                            styleElement.id = 'tempEdgeAnimation';
                            styleElement.textContent = `
                                @keyframes dashAnimation {
                                    to {
                                        stroke-dashoffset: -20;
                                    }
                                }
                            `;
                            document.head.appendChild(styleElement);
                        }
                        
                        // 显示连接确认信息
                        window.showFloatingInfo('创建连接', 
                            `从"${window.selectedSourceNodeId}"到"${clickedNodeId}"`, 'success');
                        
                        // 准备模态框数据
                        $('#edgeModalFromNode').val(window.selectedSourceNodeId);
                        $('#edgeModalToNode').val(clickedNodeId);
                        $('#edgeModalIsEditing').val('false');
                        $('#edgeModalArrayIndex').val('-1');
                        $('#edgeExpression').val('');
                        $('#edgePriority').val('0');
                        $('#deleteEdgeBtn').hide();
                        
                        // 设置模态标题
                        $('#edgePropertyModalLabel').text(i18n.translate('tasksPage.edgeModal.titleNew', 
                            "新边缘: {{from}} → {{to}}")
                            .replace('{{from}}', window.selectedSourceNodeId)
                            .replace('{{to}}', clickedNodeId));
                        
                        // 显示模态框（使用安全显示函数）
                        if (typeof window.safeShowModal === 'function') {
                            window.safeShowModal('#edgePropertyModal');
                        } else {
                            $('#edgePropertyModal').modal('show');
                        }
                        
                        // 当模态框关闭后恢复节点样式
                        $('#edgePropertyModal').one('hidden.bs.modal', function () {
                            // 恢复目标节点样式
                            parentGroup.find('rect')
                                .attr('fill', originalFill)
                                .attr('stroke', originalStroke)
                                .attr('stroke-width', originalStrokeWidth)
                                .css('filter', 'none');
                            
                            // 恢复源节点样式
                            if (window.selectedSourceElement) {
                                window.selectedSourceElement.find('rect')
                                    .attr('fill', '#fff')
                                    .attr('stroke', '#007bff')
                                    .attr('stroke-width', 2)
                                    .css('filter', 'none');
                            }
                                
                            // 清理临时动画元素
                            $('#tempAnimatedEdge').remove();
                            $('#tempEdgeAnimation').remove();
                                
                            window.selectedSourceNodeId = null;
                            window.selectedSourceElement = null;
                            
                            // 移除任何状态消息
                            $('#dagContainer').find('.node-action-status').remove();
                        });
                    }
                }
            });

            g.on('dblclick', function (event) {
                event.stopPropagation();
                const clickedNodeId = $(this).attr('data-node-id');

                let currentNodes = [];
                try {
                    currentNodes = JSON.parse($('#workflowNodesJson').val() || '[]');
                } catch (e) {
                    showFeedback(i18n.translate('tasksPage.feedback.nodeEditErrorParse', "Error parsing workflowNodesJson for editing.") + " " + e.message, true);
                    return;
                }
                const nodeIndex = currentNodes.findIndex(n => n.nodeId === clickedNodeId);
                if (nodeIndex === -1) {
                    showFeedback(i18n.translate('tasksPage.feedback.nodeEditNotFound', "Could not find node data for editing."), true);
                    return;
                }
                const nodeToEdit = currentNodes[nodeIndex];

                $('#editingNodeArrayIndex').val(nodeIndex);
                $('#displayNodeId').text(nodeToEdit.nodeId);
                
                // 加载编辑节点模态框的任务选择列表
                loadAvailableTasksForEditNode();
                
                // 设置当前已选中的任务ID
                setTimeout(function() {
                    if (nodeToEdit.taskConfigId) {
                        $('#editTaskConfigId').val(nodeToEdit.taskConfigId);
                    }
                }, 200);
                
                $('#editNodeName').val(nodeToEdit.nodeName || '');
                $('#editNodeParams').val(nodeToEdit.parameters ? JSON.stringify(nodeToEdit.parameters, null, 2) : '{}');

                $('#workflowNodeEditModalLabel').text(i18n.translate('tasksPage.nodeEditModal.title', "Edit Workflow Node: {{nodeId}}").replace('{{nodeId}}', nodeToEdit.nodeId));
                
                // 在显示节点编辑模态框前进行安全检查
                if (typeof window.safeShowModal === 'function') {
                    window.safeShowModal('#workflowNodeEditModal');
                } else {
                    $('#workflowNodeEditModal').modal('show');
                }
            });

            const textNodeName = $(document.createElementNS(svgNS, 'text'));
            textNodeName.attr('x', nodeWidth / 2).attr('y', nodeHeight / 2 - 7)
                .attr('text-anchor', 'middle').attr('dominant-baseline', 'central')
                .attr('font-size', '0.9em').attr('fill', '#333')
                .text(node.nodeName || node.nodeId);
            g.append(textNodeName);

            const textNodeId = $(document.createElementNS(svgNS, 'text'));
            textNodeId.attr('x', nodeWidth / 2).attr('y', nodeHeight / 2 + 10)
                .attr('text-anchor', 'middle').attr('dominant-baseline', 'central')
                .attr('font-size', '0.7em').attr('fill', '#666')
                .text(`(ID: ${node.nodeId})`);
            g.append(textNodeId);

            svg.append(g);
            currentY += nodeHeight + nodeVMargin;
        });

        const finalHeight = Math.max(200, currentY);
        svg.attr('height', finalHeight);
        svgContainer.append(svg);

        // 添加帮助指南
        svgContainer.append(window.showDagOperationGuide());

        // 添加工具栏
        const toolbarDiv = $('<div>')
            .addClass('dag-toolbar')
            .css({
                position: 'absolute',
                bottom: '10px',
                right: '10px',
                background: 'rgba(255, 255, 255, 0.8)',
                borderRadius: '4px',
                padding: '5px',
                border: '1px solid #ddd'
            });

        // 添加帮助按钮，点击后显示/隐藏操作指南
        const helpBtn = $('<button>')
            .addClass('btn btn-sm btn-outline-info mr-1')
            .html('<i class="bi bi-question-circle"></i>')
            .attr('title', i18n.translate('tasksPage.workflowToolbar.helpTitle', '显示帮助'))
            .on('click', function() {
                const guide = svgContainer.find('.dag-operation-guide');
                if (guide.length) {
                    guide.toggle();
                } else {
                    svgContainer.append(window.showDagOperationGuide());
                }
            });

        toolbarDiv.append(helpBtn);
        svgContainer.append(toolbarDiv);

        // 收集节点位置以便绘制边缘
        const nodePositions = {};
        svg.find('g.node-group').each(function () {
            const g = $(this);
            nodePositions[g.attr('data-node-id')] = g.data('nodeCenter');
        });

        if (Object.keys(nodePositions).length > 0 && nodesArray.length > 0) {
            drawWorkflowEdges(svg, nodePositions);
        }
    };

    // 绘制工作流边缘
    window.drawWorkflowEdges = function(svg, nodePositions) {
        const svgNS = 'http://www.w3.org/2000/svg';
        let edgesJson = $('#workflowEdgesJson').val();
        let edgesArray = [];

        if (edgesJson && edgesJson.trim() !== '') {
            try {
                edgesArray = JSON.parse(edgesJson);
                if (!Array.isArray(edgesArray)) {
                    showFeedback(i18n.translate('tasksPage.modal.workflowTaskFields.dagContainerErrorNotArray'), true);
                    edgesArray = [];
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.modal.workflowTaskFields.dagContainerErrorParseEdges', "Error parsing Workflow Edges JSON: {{message}}").replace("{{message}}", e.message), true);
                return;
            }
        }

        // 先清除之前的边缘
        svg.find('g.dag-edge-group').remove();

        edgesArray.forEach(function (edge, index) {
            const sourcePos = nodePositions[edge.fromNodeId];
            const targetPos = nodePositions[edge.toNodeId];

            if (sourcePos && targetPos) {
                // 创建连线组
                const g = $(document.createElementNS(svgNS, 'g'));
                g.addClass('dag-edge-group');
                
                // 计算线的方向和偏移，使边缘更清晰
                let dx = targetPos.x - sourcePos.x;
                let dy = targetPos.y - sourcePos.y;
                const length = Math.sqrt(dx * dx + dy * dy);
                
                // 标准化方向向量
                dx = dx / length;
                dy = dy / length;
                
                // 将起点稍微移出源节点，结束点稍微移入目标节点，使箭头更清晰
                const offsetStart = 30; // 源节点偏移
                const offsetEnd = 35;   // 目标节点偏移（留出箭头空间）
                
                const adjustedStartX = sourcePos.x + dx * offsetStart;
                const adjustedStartY = sourcePos.y + dy * offsetStart;
                const adjustedEndX = targetPos.x - dx * offsetEnd;
                const adjustedEndY = targetPos.y - dy * offsetEnd;

                // 创建一条更宽的透明线作为点击/悬停区域
                const hitArea = $(document.createElementNS(svgNS, 'line'));
                hitArea.attr('x1', adjustedStartX)
                    .attr('y1', adjustedStartY)
                    .attr('x2', adjustedEndX)
                    .attr('y2', adjustedEndY)
                    .attr('stroke', 'transparent')
                    .attr('stroke-width', 15)
                    .css('cursor', 'pointer');
                
                // 创建实际可见的线
                const line = $(document.createElementNS(svgNS, 'line'));
                line.attr('x1', adjustedStartX)
                    .attr('y1', adjustedStartY)
                    .attr('x2', adjustedEndX)
                    .attr('y2', adjustedEndY)
                    .attr('stroke', '#333')
                    .attr('stroke-width', 2)
                    .attr('marker-end', 'url(#arrowhead)')
                    .addClass('dag-edge');
                
                // 如果有表达式，添加带背景的文本标签，使其更清晰可读
                if (edge.expression) {
                    // 计算文本位置 - 在线的中点
                    const textX = (adjustedStartX + adjustedEndX) / 2;
                    const textY = (adjustedStartY + adjustedEndY) / 2;
                    
                    // 文本背景 - 创建一个白色背景矩形，使文本易于阅读
                    const textBg = $(document.createElementNS(svgNS, 'rect'));

                    // 文本内容
                    const displayText = edge.expression.length > 20 ? edge.expression.substring(0, 20) + '...' : edge.expression;
                    const text = $(document.createElementNS(svgNS, 'text'));
                    text.attr('x', textX)
                        .attr('y', textY - 10) // 稍微向上偏移，不要挡住线
                        .attr('text-anchor', 'middle')
                        .attr('dominant-baseline', 'middle')
                        .attr('font-size', '0.8em')
                        .attr('fill', '#444')
                        .text(displayText);
                    
                    // 添加优先级标签（如果不为0）
                    if (edge.priority && edge.priority !== 0) {
                        const priorityText = $(document.createElementNS(svgNS, 'text'));
                        priorityText.attr('x', textX)
                            .attr('y', textY + 8) // 优先级显示在表达式下方
                            .attr('text-anchor', 'middle')
                            .attr('dominant-baseline', 'middle')
                            .attr('font-size', '0.75em')
                            .attr('fill', '#666')
                            .text(`优先级: ${edge.priority}`);
                        
                        g.append(priorityText);
                    }
                    
                    g.append(text);

                    // 尝试在添加到DOM后自适应调整背景矩形大小
                    setTimeout(() => {
                        try {
                            const textNode = text[0];
                            if (textNode) {
                                const bbox = textNode.getBBox();
                                textBg.attr('x', bbox.x - 5)
                                    .attr('y', bbox.y - 2)
                                    .attr('width', bbox.width + 10)
                                    .attr('height', bbox.height + 4)
                                    .attr('fill', 'rgba(255,255,255,0.8)')
                                    .attr('rx', 3);
                                g.prepend(textBg);
                            }
                        } catch (e) {
                            console.warn('Error sizing text background:', e);
                        }
                    }, 0);
                }

                // 存储边缘数据
                g.data('edgeData', edge);
                g.data('edgeArrayIndex', index);
                g.addClass('dag-edge-container');
                
                // 添加悬停效果
                g.on('mouseenter', function() {
                    $(this).find('.dag-edge').attr('stroke', '#007bff').attr('stroke-width', 3);
                }).on('mouseleave', function() {
                    // 如果不是当前选中的，恢复原样
                    if (!$(this).hasClass('selected-edge')) {
                        $(this).find('.dag-edge').attr('stroke', '#333').attr('stroke-width', 2);
                    }
                });
                
                // 将所有元素添加到组中
                g.append(hitArea);
                g.append(line);

                // 添加点击事件处理
                g.on('click', function (event) {
                    event.stopPropagation();
                    const edgeData = $(this).data('edgeData');
                    const edgeIndex = $(this).data('edgeArrayIndex');
                    
                    // 填充模态窗口数据
                    $('#edgeModalFromNode').val(edgeData.fromNodeId);
                    $('#edgeModalToNode').val(edgeData.toNodeId);
                    $('#edgeModalIsEditing').val('true');
                    $('#edgeModalArrayIndex').val(edgeIndex);
                    $('#edgeExpression').val(edgeData.expression || '');
                    $('#edgePriority').val(edgeData.priority || 0);
                    $('#deleteEdgeBtn').show();
                    
                    // 设置模态窗口标题
                    $('#edgePropertyModalLabel').text(i18n.translate('tasksPage.edgeModal.titleEdit', 
                        "Edit Edge: {{from}} → {{to}}")
                        .replace('{{from}}', edgeData.fromNodeId)
                        .replace('{{to}}', edgeData.toNodeId));
                    
                    // 显示模态窗口
                    $('#edgePropertyModal').modal('show');
                    
                    // 移除之前选中的边缘
                    $('.dag-edge-container').removeClass('selected-edge');
                    $('.dag-edge').attr('stroke', '#333').attr('stroke-width', 2);
                    
                    // 高亮显示当前选中的边缘
                    $(this).addClass('selected-edge');
                    $(this).find('.dag-edge').attr('stroke', '#007bff').attr('stroke-width', 3);
                });

                // 将整个组添加到SVG中
                svg.append(g);
            } else {
                console.warn(`Could not draw edge, missing node position for: ${edge.fromNodeId} or ${edge.toNodeId}`);
            }
        });
    };

    // 初始化全局变量
    window.selectedSourceNodeId = null;
    window.selectedSourceElement = null;
    window.initialGroupPopulationDone = false;

    // 添加全局样式
    (function() {
        // 添加CSS样式到文档头部
        const styleElement = document.createElement('style');
        styleElement.type = 'text/css';
        styleElement.innerHTML = `
            /* DAG节点和边缘样式 */
            .dag-node rect {
                transition: fill 0.3s, stroke 0.3s, stroke-width 0.2s;
                cursor: pointer;
            }
            
            .dag-node:hover rect {
                stroke-width: 3px;
                stroke: #28a745;
            }
            
            .dag-edge {
                transition: stroke 0.3s, stroke-width 0.2s;
                cursor: pointer;
            }
            
            .dag-edge-group:hover .dag-edge {
                stroke: #007bff;
                stroke-width: 3px;
            }
            
            .selected-edge .dag-edge {
                stroke: #007bff;
                stroke-width: 3px;
            }
            
            .node-action-status {
                animation: fadeIn 0.5s;
                border: 1px solid rgba(0, 0, 0, 0.1);
            }
            
            @keyframes fadeIn {
                from { opacity: 0; transform: translateY(-10px) translateX(-50%); }
                to { opacity: 1; transform: translateY(0) translateX(-50%); }
            }
            
            /* DAG相关模态框样式增强 */
            #edgePropertyModal .modal-header,
            #workflowNodeEditModal .modal-header {
                background-color: #f8f9fa;
                border-bottom: 1px solid #e9ecef;
            }
            
            /* 上下文菜单样式 */
            .dag-context-menu {
                user-select: none;
            }
            
            .dag-context-menu-item {
                transition: background-color 0.2s;
            }
            
            /* 操作提示样式 */
            .dag-operation-guide {
                animation: slideIn 0.3s ease-out;
                max-height: 80vh;
                overflow-y: auto;
            }
            
            @keyframes slideIn {
                from { opacity: 0; transform: translateX(20px); }
                to { opacity: 1; transform: translateX(0); }
            }
            
            /* 编辑模式切换按钮样式 */
            #editorModeForm.active,
            #editorModeGraph.active {
                background-color: #007bff;
                color: white;
                border-color: #007bff;
            }
            
            /* 拖拽状态样式 */
            .dragging {
                cursor: move !important;
            }
            
            /* 图形模式工具栏样式 */
            #graphModeControls {
                transition: all 0.3s ease;
            }
            
            /* 节点类型样式 */
            .dag-node[data-node-type="start"] rect {
                fill: #d1e7dd;
                stroke: #20c997;
            }
            
            .dag-node[data-node-type="end"] rect {
                fill: #f8d7da;
                stroke: #dc3545;
            }
            
            .dag-node[data-node-type="decision"] rect {
                fill: #fff3cd;
                stroke: #ffc107;
            }
            
            .dag-node[data-node-type="parallel"] rect {
                fill: #cfe2ff;
                stroke: #0d6efd;
            }
            
            .dag-node[data-node-type="process"] rect {
                fill: #e2e3e5;
                stroke: #6c757d;
            }
            
            /* 放大缩小按钮样式 */
            #zoomInBtn, #zoomOutBtn, #resetZoomBtn {
                width: 32px;
                height: 32px;
                padding: 0;
                font-weight: bold;
            }
            
            /* DAG容器增强样式 */
            #dagContainer {
                transition: background-color 0.3s;
            }
            
            #dagContainer.graph-mode {
                background-color: #f0f0f0;
                background-image: 
                    linear-gradient(rgba(200, 200, 200, 0.1) 1px, transparent 1px),
                    linear-gradient(90deg, rgba(200, 200, 200, 0.1) 1px, transparent 1px);
                background-size: 20px 20px;
            }
            
            /* 表达式编辑帮助样式 */
            #expressionHelpText code {
                background-color: #f8f9fa;
                padding: 1px 4px;
                border-radius: 3px;
                color: #d63384;
                font-size: 0.9em;
            }
        `;
        document.head.appendChild(styleElement);
    })();

    console.log('tasks workflow脚本初始化完成');
}

// 初始化图形模式
// 全局函数：处理节点助手的可见性
window.ensureNodeHelperVisible = function(forceShow) {
    // 始终确保工作流任务字段可见
    if (!$('#workflowTaskFields').is(':visible')) {
        $('#workflowTaskFields').show();
    }
    
    const nodeHelper = $('#nodeHelperCard');
    const nodeHelperContent = $('#nodeHelperContent');
    
    // 始终确保卡片标题可见，以便用户能够切换显示/隐藏
    nodeHelper.show();
    
    // 如果forceShow为true，或者用户没有手动隐藏过节点助手，则显示节点助手内容
    if (forceShow || (localStorage.getItem('nodeHelperHidden') !== 'true')) {
        nodeHelperContent.show();
        $('#toggleNodeHelper i').removeClass('bi-chevron-down').addClass('bi-chevron-up');
        console.log("Node helper visibility: 显示内容");
    } else {
        // 内容区域隐藏，但保留卡片标题可见
        nodeHelperContent.hide();
        $('#toggleNodeHelper i').removeClass('bi-chevron-up').addClass('bi-chevron-down');
        console.log("Node helper visibility: 隐藏内容");
    }
};

window.initGraphMode = function() {
    window.graphModeEnabled = true;
    window.nodePositionChanged = false;
    
    // 重新绘制DAG以启用拖拽功能
    redrawDAG(true);
    
    // 确保节点助手卡片在图形模式下可见
    window.ensureNodeHelperVisible();
    console.log("Graph Mode Init: Making node helper visible");
    
    // 添加拖拽时的鼠标跟踪
    $('#dagContainer').on('mousemove.graphMode', handleMouseMove);
    $('#dagContainer').on('mouseup.graphMode', handleMouseUp);
    
    // 添加拖拽操作的视觉反馈和监听器
    window.applyDragVisualFeedback();
    
    // 添加操作指南指示
    const guideContainer = window.showDagOperationGuide();
    $('#dagContainer').append(guideContainer);
    
    // 在5秒后淡出指南
    setTimeout(() => {
        guideContainer.fadeOut(1000);
    }, 5000);
    
    console.log('已启用图形编辑模式');
};

// 退出图形模式
window.exitGraphMode = function() {
    window.graphModeEnabled = false;
    
    // 移除图形模式特定的事件处理器
    $('#dagContainer').off('mousemove.graphMode');
    $('#dagContainer').off('mouseup.graphMode');
    
    // 重置变量
    window.svgDraggable = false;
    window.nodeDragging = false;
    window.currentDragNode = null;
    
    console.log('已禁用图形编辑模式');
};

// 处理鼠标移动事件（用于拖拽）
function handleMouseMove(event) {
    if (window.nodeDragging && window.currentDragNode) {
        event.preventDefault();
        
        const svg = $('#dagContainer svg');
        const svgOffset = svg.offset();
        
        // 计算鼠标在SVG坐标系内的位置
        const mouseX = (event.pageX - svgOffset.left) / window.currentScale;
        const mouseY = (event.pageY - svgOffset.top) / window.currentScale;
        
        // 移动节点
        const nodeGroup = window.currentDragNode;
        const nodeWidth = 150; // 与创建节点时相同
        const nodeHeight = 60; // 与创建节点时相同
        
        // 更新节点位置，添加网格吸附功能
        const gridSize = 10; // 10px的网格
        const newX = Math.round((mouseX - (nodeWidth / 2)) / gridSize) * gridSize;
        const newY = Math.round((mouseY - (nodeHeight / 2)) / gridSize) * gridSize;
        
        // 添加移动动画，使拖拽更流畅
        nodeGroup.css('transition', 'transform 0.05s ease-out');
        nodeGroup.attr('transform', `translate(${newX}, ${newY})`);
        
        // 更新节点中心点
        const nodeCenterX = newX + nodeWidth / 2;
        const nodeCenterY = newY + nodeHeight / 2;
        nodeGroup.data('nodeCenter', {x: nodeCenterX, y: nodeCenterY});
        
        // 记录位置已更改
        window.nodePositionChanged = true;
        
        // 更新连接到此节点的边
        updateConnectedEdges(nodeGroup.attr('data-node-id'));
        
        // 显示拖拽反馈
        showDragFeedback(nodeGroup);
    }
}

// 显示拖拽反馈
function showDragFeedback(nodeGroup) {
    // 已存在提示则不重复创建
    if ($('#dragPositionFeedback').length) {
        const nodeId = nodeGroup.attr('data-node-id');
        const transform = nodeGroup.attr('transform');
        const match = /translate\(([^,]+),\s*([^)]+)\)/.exec(transform);
        
        if (match) {
            const x = Math.round(parseFloat(match[1]));
            const y = Math.round(parseFloat(match[2]));
            $('#dragPositionFeedback').text(`${nodeId}: x=${x}, y=${y}`);
        }
        return;
    }
    
    // 创建拖拽反馈提示
    const feedbackDiv = $('<div>')
        .attr('id', 'dragPositionFeedback')
        .css({
            position: 'absolute',
            bottom: '10px',
            right: '10px',
            background: 'rgba(0, 0, 0, 0.7)',
            color: 'white',
            padding: '4px 8px',
            borderRadius: '4px',
            fontSize: '0.8em',
            pointerEvents: 'none'
        });
    
    $('#dagContainer').append(feedbackDiv);
}

// 处理鼠标松开事件（结束拖拽）
function handleMouseUp(event) {
    if (window.nodeDragging) {
        const draggedNode = window.currentDragNode;
        
        // 移除拖拽状态
        window.nodeDragging = false;
        window.currentDragNode = null;
        $('body').css('cursor', 'default');
        
        if (draggedNode) {
            // 移除拖拽时的动画效果
            draggedNode.css('transition', 'none');
            
            // 恢复节点视觉样式
            draggedNode.find('rect').attr({
                'stroke': '#007bff',
                'stroke-width': 2,
                'fill': '#fff'
            });
        }
        
        // 保存当前所有节点位置
        saveNodePositions();
        
        // 移除拖拽反馈
        setTimeout(() => {
            $('#dragPositionFeedback').fadeOut(300, function() {
                $(this).remove();
            });
        }, 1000);
    }
}

// 更新与特定节点连接的边
function updateConnectedEdges(nodeId) {
    const nodePositions = {};
    $('#dagContainer svg').find('g.node-group').each(function() {
        const g = $(this);
        nodePositions[g.attr('data-node-id')] = g.data('nodeCenter');
    });
    
    // 重新绘制边
    $('#dagContainer svg').find('g.dag-edge-group').each(function() {
        const edgeGroup = $(this);
        const edgeData = edgeGroup.data('edgeData');
        
        if (edgeData.fromNodeId === nodeId || edgeData.toNodeId === nodeId) {
            const sourcePos = nodePositions[edgeData.fromNodeId];
            const targetPos = nodePositions[edgeData.toNodeId];
            
            if (sourcePos && targetPos) {
                // 计算新位置
                let dx = targetPos.x - sourcePos.x;
                let dy = targetPos.y - sourcePos.y;
                const length = Math.sqrt(dx * dx + dy * dy);
                
                // 标准化方向向量
                dx = dx / length;
                dy = dy / length;
                
                // 偏移量
                const offsetStart = 30;
                const offsetEnd = 35;
                
                const adjustedStartX = sourcePos.x + dx * offsetStart;
                const adjustedStartY = sourcePos.y + dy * offsetStart;
                const adjustedEndX = targetPos.x - dx * offsetEnd;
                const adjustedEndY = targetPos.y - dy * offsetEnd;
                
                // 更新线条位置
                edgeGroup.find('line').each(function() {
                    $(this)
                        .attr('x1', adjustedStartX)
                        .attr('y1', adjustedStartY)
                        .attr('x2', adjustedEndX)
                        .attr('y2', adjustedEndY);
                });
                
                // 更新文本位置
                if (edgeData.expression) {
                    const textX = (adjustedStartX + adjustedEndX) / 2;
                    const textY = (adjustedStartY + adjustedEndY) / 2;
                    
                    edgeGroup.find('text').each(function() {
                        $(this)
                            .attr('x', textX)
                            .attr('y', $(this).attr('dominant-baseline') === 'middle' ? textY + 8 : textY - 10);
                    });
                    
                    // 更新文本背景
                    setTimeout(() => {
                        try {
                            const textNode = edgeGroup.find('text:first')[0];
                            if (textNode) {
                                const bbox = textNode.getBBox();
                                edgeGroup.find('rect').attr({
                                    x: bbox.x - 5,
                                    y: bbox.y - 2,
                                    width: bbox.width + 10,
                                    height: bbox.height + 4
                                });
                            }
                        } catch (e) {
                            console.warn('Error updating text background:', e);
                        }
                    }, 0);
                }
            }
        }
    });
}

// 保存节点位置
function saveNodePositions() {
    window.nodePositionsMap = {};
    
    $('#dagContainer svg').find('g.node-group').each(function() {
        const nodeGroup = $(this);
        const nodeId = nodeGroup.attr('data-node-id');
        const transform = nodeGroup.attr('transform');
        
        // 解析transform属性获取位置
        const match = /translate\(([^,]+),\s*([^)]+)\)/.exec(transform);
        if (match) {
            const x = parseFloat(match[1]);
            const y = parseFloat(match[2]);
            
            window.nodePositionsMap[nodeId] = {x: x, y: y};
        }
    });
    
    console.log('已保存节点位置:', window.nodePositionsMap);
}

// 应用图形模式的更改到JSON
window.applyGraphModeChanges = function() {
    if (!window.nodePositionChanged) {
        showFeedback(i18n.translate('tasksPage.feedback.noChangesToApply', '没有需要应用的更改'), false);
        return;
    }
    
    try {
        // 获取当前节点定义
        const nodesJson = $('#workflowNodesJson').val();
        const nodesArray = JSON.parse(nodesJson || '[]');
        
        // 应用已保存的位置
        for (let i = 0; i < nodesArray.length; i++) {
            const nodeId = nodesArray[i].nodeId;
            
            if (window.nodePositionsMap[nodeId]) {
                // 添加位置信息到节点
                nodesArray[i].position = window.nodePositionsMap[nodeId];
            }
        }
        
        // 更新JSON
        $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
        window.nodePositionChanged = false;
        
        showFeedback(i18n.translate('tasksPage.feedback.changesApplied', '已应用所有更改到工作流定义'), false);
    } catch (e) {
        showFeedback(i18n.translate('tasksPage.feedback.errorApplyingChanges', '应用更改时出错: ') + e.message, true);
    }
};

// 确保工作流更改保存 - 在保存工作流前调用此函数确保所有节点位置更改都被应用
window.ensureWorkflowChangesSaved = function() {
    console.log('准备保存工作流，确保所有更改已应用');
    try {
        // 关闭打开的模态窗口
        const openModals = $('.modal.show');
        if (openModals.length > 0) {
            console.log(`保存工作流前关闭 ${openModals.length} 个打开的模态窗口`);
            openModals.modal('hide');
        }

        // 如果节点位置有变更，自动应用更改
        if (window.nodePositionChanged && typeof window.applyGraphModeChanges === 'function') {
            console.log('自动应用工作流节点位置更改');
            window.applyGraphModeChanges();
        }
        
        // 确保 JSON 数据是有效的且符合工作流要求
        const nodesJson = $('#workflowNodesJson').val();
        const edgesJson = $('#workflowEdgesJson').val();
        
        // 验证节点数据
        if (!nodesJson || nodesJson.trim() === '') {
            console.error("节点数据为空");
            showFeedback(i18n.translate('tasksPage.feedback.nodesJsonEmpty', "工作流节点数据不能为空"), true);
            return false;
        }
        
        let nodes = [];
        try {
            nodes = JSON.parse(nodesJson);
            if (!Array.isArray(nodes)) {
                console.error("节点数据不是数组");
                showFeedback(i18n.translate('tasksPage.feedback.nodesJsonNotArray', "工作流节点数据必须是数组"), true);
                return false;
            }
            
            if (nodes.length === 0) {
                console.error("节点数组为空");
                showFeedback(i18n.translate('tasksPage.feedback.nodesArrayEmpty', "工作流必须至少包含一个节点"), true);
                return false;
            }
            
            console.log(`工作流节点数据有效，共 ${nodes.length} 个节点`);
        } catch (e) {
            console.error("工作流节点 JSON 无效:", e);
            showFeedback(i18n.translate('tasksPage.feedback.nodesJsonInvalid', "工作流节点 JSON 格式无效，请检查") + ": " + e.message, true);
            return false;
        }
        
        // 验证边缘数据
        if (edgesJson && edgesJson.trim() !== '') {
            try {
                const edges = JSON.parse(edgesJson);
                if (!Array.isArray(edges)) {
                    console.error("边缘数据不是数组");
                    showFeedback(i18n.translate('tasksPage.feedback.edgesJsonNotArray', "工作流边缘数据必须是数组"), true);
                    return false;
                }
                console.log(`工作流边数据有效，共 ${edges.length} 条边`);
            } catch (e) {
                console.error("工作流边 JSON 无效:", e);
                showFeedback(i18n.translate('tasksPage.feedback.edgesJsonInvalid', "工作流边 JSON 格式无效，请检查") + ": " + e.message, true);
                return false;
            }
        }
        
        // 进行安全检查
        if (typeof window.checkWorkflowSafetyState === 'function') {
            console.log("执行工作流保存前的安全检查");
            window.checkWorkflowSafetyState();
        }
        
        // 清理任何可能干扰保存的状态
        window.selectedSourceNodeId = null;
        window.selectedSourceElement = null;
        
        // 移除所有临时元素
        $('#tempAnimatedEdge, #tempEdgeAnimation, .node-action-status, #edgeVisualGuide').remove();
        $('#dagContainer').off('mousemove.edgeGuide');
        
        console.log('工作流准备好保存了');
        return true;
    } catch (e) {
        console.error("保存工作流前处理失败:", e);
        showFeedback(i18n.translate('tasksPage.feedback.prepareWorkflowError', "准备保存工作流时出错: {{error}}").replace("{{error}}", e.message), true);
        return false;
    }
};

// 放大/缩小DAG图
window.zoomDag = function(factor) {
    const svgContainer = $('#dagContainer');
    const svg = svgContainer.find('svg');
    
    if (svg.length) {
        window.currentScale *= factor;
        
        // 应用缩放变换
        const currentTransform = svg.attr('transform') || '';
        const scalePattern = /scale\([^)]+\)/;
        
        if (scalePattern.test(currentTransform)) {
            // 替换已有的缩放
            svg.attr('transform', currentTransform.replace(scalePattern, `scale(${window.currentScale})`));
        } else {
            // 添加新的缩放
            svg.attr('transform', `${currentTransform} scale(${window.currentScale})`);
        }
    }
};

// 重置缩放
window.resetZoom = function() {
    const svgContainer = $('#dagContainer');
    const svg = svgContainer.find('svg');
    
    if (svg.length) {
        window.currentScale = 1.0;
        
        // 重置变换
        const currentTransform = svg.attr('transform') || '';
        svg.attr('transform', currentTransform.replace(/\s*scale\([^)]+\)/, ''));
    }
};

// 自动排版节点
window.arrangeNodesAutoLayout = function() {
    try {
        // 读取当前节点
        let nodesArray = [];
        const nodesJson = $('#workflowNodesJson').val();
        
        if (!nodesJson || nodesJson.trim() === '') {
            showFeedback(i18n.translate('tasksPage.feedback.noNodesToLayout', '没有节点可以布局'), false);
            return;
        }
        
        try {
            nodesArray = JSON.parse(nodesJson);
        } catch(e) {
            showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析节点JSON时出错'), true);
            return;
        }
        
        if (!Array.isArray(nodesArray) || nodesArray.length === 0) {
            showFeedback(i18n.translate('tasksPage.feedback.noNodesToLayout', '没有节点可以布局'), false);
            return;
        }
        
        // 读取边缘数据
        let edgesArray = [];
        const edgesJson = $('#workflowEdgesJson').val();
        
        if (edgesJson && edgesJson.trim() !== '') {
            try {
                edgesArray = JSON.parse(edgesJson);
            } catch(e) {
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingEdges', '解析边缘JSON时出错'), true);
                edgesArray = [];
            }
        }
        
        // 检测特殊节点
        const nodeMap = {};
        let startNodes = [];
        let endNodes = [];
        
        nodesArray.forEach(node => {
            nodeMap[node.nodeId] = node;
            
            // 检测开始节点
            if (
                node.nodeId.toLowerCase().includes('start') || 
                (node.parameters && node.parameters.isStartNode) ||
                node.nodeName.toLowerCase().includes('开始') ||
                node.nodeName.toLowerCase().includes('start')
            ) {
                startNodes.push(node.nodeId);
            }
            
            // 检测结束节点
            if (
                node.nodeId.toLowerCase().includes('end') || 
                (node.parameters && node.parameters.isEndNode) ||
                node.nodeName.toLowerCase().includes('结束') ||
                node.nodeName.toLowerCase().includes('end')
            ) {
                endNodes.push(node.nodeId);
            }
        });
        
        // 如果没有明确的起始节点，找出入度为0的节点作为起始点
        if (startNodes.length === 0) {
            const inDegree = {};
            nodesArray.forEach(node => {
                inDegree[node.nodeId] = 0;
            });
            
            edgesArray.forEach(edge => {
                if (edge.toNodeId && inDegree[edge.toNodeId] !== undefined) {
                    inDegree[edge.toNodeId]++;
                }
            });
            
            for (const nodeId in inDegree) {
                if (inDegree[nodeId] === 0) {
                    startNodes.push(nodeId);
                }
            }
        }
        
        // 如果仍然没有起始节点，使用第一个节点作为起始
        if (startNodes.length === 0 && nodesArray.length > 0) {
            startNodes.push(nodesArray[0].nodeId);
        }
        
        // 如果没有明确的结束节点，找出出度为0的节点作为结束点
        if (endNodes.length === 0) {
            const outDegree = {};
            nodesArray.forEach(node => {
                outDegree[node.nodeId] = 0;
            });
            
            edgesArray.forEach(edge => {
                if (edge.fromNodeId && outDegree[edge.fromNodeId] !== undefined) {
                    outDegree[edge.fromNodeId]++;
                }
            });
            
            for (const nodeId in outDegree) {
                if (outDegree[nodeId] === 0) {
                    endNodes.push(nodeId);
                }
            }
        }
        
        // 构建邻接列表
        const adjList = {};
        nodesArray.forEach(node => {
            adjList[node.nodeId] = [];
        });
        
        edgesArray.forEach(edge => {
            if (edge.fromNodeId && edge.toNodeId) {
                if (adjList[edge.fromNodeId]) {
                    adjList[edge.fromNodeId].push(edge.toNodeId);
                }
            }
        });
        
        // 构建反向邻接列表（用于查找前驱）
        const revAdjList = {};
        nodesArray.forEach(node => {
            revAdjList[node.nodeId] = [];
        });
        
        edgesArray.forEach(edge => {
            if (edge.fromNodeId && edge.toNodeId) {
                if (revAdjList[edge.toNodeId]) {
                    revAdjList[edge.toNodeId].push(edge.fromNodeId);
                }
            }
        });
        
        // 层级分配算法
        const nodeLevels = {};
        const visited = new Set();
        
        function assignLevel(nodeId, level) {
            if (visited.has(nodeId)) return;
            visited.add(nodeId);
            
            nodeLevels[nodeId] = Math.max(level, nodeLevels[nodeId] || 0);
            
            for (const nextNodeId of adjList[nodeId] || []) {
                assignLevel(nextNodeId, level + 1);
            }
        }
        
        // 从所有起始节点出发，分配层级
        startNodes.forEach(startNodeId => {
            assignLevel(startNodeId, 0);
        });
        
        // 可能存在没有被访问到的节点（孤岛或环），为它们分配层级
        nodesArray.forEach(node => {
            if (!visited.has(node.nodeId)) {
                // 尝试根据前驱节点分配层级
                const predecessors = revAdjList[node.nodeId] || [];
                let level = 0;
                
                if (predecessors.length > 0) {
                    // 如果有前驱，则在最大前驱层级的下一层
                    level = Math.max(...predecessors.map(p => (nodeLevels[p] || 0))) + 1;
                } else {
                    // 否则，作为起始节点
                    level = 0;
                }
                
                assignLevel(node.nodeId, level);
            }
        });
        
        // 计算每层的节点
        const levelNodes = {};
        for (const nodeId in nodeLevels) {
            const level = nodeLevels[nodeId];
            if (!levelNodes[level]) {
                levelNodes[level] = [];
            }
            levelNodes[level].push(nodeId);
        }
        
        // 分配节点位置
        const nodeWidth = 150;
        const nodeHeight = 60;
        const verticalGap = 80;
        const horizontalGap = 40;
        
        // 计算每层的宽度
        const maxNodesInLevel = Math.max(...Object.values(levelNodes).map(nodes => nodes.length));
        const levelWidth = (maxNodesInLevel * nodeWidth) + ((maxNodesInLevel - 1) * horizontalGap);
        
        // 更新节点位置
        for (const level in levelNodes) {
            const nodes = levelNodes[level];
            const levelY = parseInt(level) * (nodeHeight + verticalGap) + 50;
            
            // 计算水平位置
            const levelNodesCount = nodes.length;
            const totalLevelWidth = (levelNodesCount * nodeWidth) + ((levelNodesCount - 1) * horizontalGap);
            const startX = (levelWidth - totalLevelWidth) / 2;
            
            nodes.forEach((nodeId, index) => {
                const nodeX = startX + (index * (nodeWidth + horizontalGap));
                if (nodeMap[nodeId]) {
                    nodeMap[nodeId].position = {
                        x: nodeX,
                        y: levelY
                    };
                }
            });
        }
        
        // 更新节点JSON
        $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
        
        // 触发重绘
        redrawDAG();
        
        // 视觉反馈
        window.showFloatingInfo('自动布局完成', '所有节点已根据连接关系重新排布', 'success');
    } catch(e) {
        console.error('自动布局出错:', e);
        showFeedback(i18n.translate('tasksPage.feedback.errorAutoLayout', '执行自动布局时出错'), true);
    }
};

// 为边缘创建添加视觉指引
window.createEdgeVisualGuide = function() {
    // 清除可能存在的旧指引
    $('#edgeVisualGuide').remove();
    
    if (window.selectedSourceNodeId) {
        // 获取源节点中心位置
        const sourceNode = $(`g.node-group[data-node-id="${window.selectedSourceNodeId}"]`);
        const sourcePos = sourceNode.data('nodeCenter');
        
        if (sourcePos) {
            const svgNS = 'http://www.w3.org/2000/svg';
            const guideGroup = $(document.createElementNS(svgNS, 'g'))
                .attr('id', 'edgeVisualGuide')
                .css('pointer-events', 'none');
            
            // 创建虚线以指示正在创建连接
            const guideLine = $(document.createElementNS(svgNS, 'line'))
                .attr({
                    'x1': sourcePos.x,
                    'y1': sourcePos.y,
                    'x2': sourcePos.x,
                    'y2': sourcePos.y,
                    'stroke': '#28a745',
                    'stroke-width': 2,
                    'stroke-dasharray': '5,5'
                });
                
            guideGroup.append(guideLine);
            
            // 添加箭头指示
            const markerGroup = $(document.createElementNS(svgNS, 'g'))
                .attr('transform', `translate(${sourcePos.x}, ${sourcePos.y})`);
                
            const arrowTip = $(document.createElementNS(svgNS, 'circle'))
                .attr({
                    'r': 5,
                    'fill': '#28a745'
                });
                
            markerGroup.append(arrowTip);
            guideGroup.append(markerGroup);
            
            // 添加说明文字
            const text = $(document.createElementNS(svgNS, 'text'))
                .attr({
                    'x': sourcePos.x + 10,
                    'y': sourcePos.y - 10,
                    'fill': '#28a745',
                    'font-size': '0.8em'
                })
                .text(i18n.translate('tasksPage.nodeAction.selectTarget', '点击目标节点创建连接'));
            
            guideGroup.append(text);
            
            // 将指引添加到SVG
            $('#dagContainer svg').append(guideGroup);
            
            // 更新鼠标移动监听，在移动时更新指引
            $('#dagContainer').on('mousemove.edgeGuide', function(event) {
                const svg = $('#dagContainer svg');
                const svgOffset = svg.offset();
                
                // 计算鼠标在SVG坐标系内的位置
                const mouseX = (event.pageX - svgOffset.left) / window.currentScale;
                const mouseY = (event.pageY - svgOffset.top) / window.currentScale;
                
                // 更新线条位置
                guideLine.attr({
                    'x2': mouseX,
                    'y2': mouseY
                });
                
                // 更新箭头位置
                markerGroup.attr('transform', `translate(${mouseX}, ${mouseY})`);
            });
        }
    }
};

// 移除边缘创建视觉指引
window.removeEdgeVisualGuide = function() {
    $('#edgeVisualGuide').remove();
    $('#dagContainer').off('mousemove.edgeGuide');
};

// 增强节点选择交互
window.enhanceNodeSelection = function(nodeGroup, clickedNodeId) {
    // 视觉反馈 - 源节点高亮
    nodeGroup.find('rect')
        .attr({
            'fill': '#cce5ff',
            'stroke': '#007bff',
            'stroke-width': 3
        })
        .css('filter', 'drop-shadow(0 0 3px rgba(0, 123, 255, 0.5))');
    
    // 创建和显示视觉指引
    window.createEdgeVisualGuide();
    
    // 显示信息提示
    window.showFloatingInfo('已选择源节点', '点击目标节点创建连接，或再次点击此节点取消', 'info');
};

// 显示浮动信息提示
window.showFloatingInfo = function(title, message, type = 'info') {
    // 如果已存在，先移除旧的提示
    $('.workflow-floating-info').remove();
    
    // 确定提示样式
    let bgColor, iconClass, textColor;
    switch (type) {
        case 'success':
            bgColor = '#d4edda';
            textColor = '#155724';
            iconClass = 'bi-check-circle-fill';
            break;
        case 'warning':
            bgColor = '#fff3cd';
            textColor = '#856404';
            iconClass = 'bi-exclamation-triangle-fill';
            break;
        case 'error':
            bgColor = '#f8d7da';
            textColor = '#721c24';
            iconClass = 'bi-x-circle-fill';
            break;
        default: // info
            bgColor = '#cce5ff';
            textColor = '#004085';
            iconClass = 'bi-info-circle-fill';
    }
    
    // 创建提示元素
    const infoDiv = $('<div>')
        .addClass('workflow-floating-info')
        .css({
            position: 'absolute',
            top: '15px',
            left: '50%',
            transform: 'translateX(-50%)',
            padding: '8px 15px',
            background: bgColor,
            color: textColor,
            borderRadius: '4px',
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
            zIndex: 1050,
            maxWidth: '80%',
            display: 'flex',
            alignItems: 'center',
            fontSize: '0.9em',
            opacity: 0,
            transition: 'opacity 0.3s ease-in-out'
        });
    
    // 添加图标
    const icon = $('<i>')
        .addClass(`bi ${iconClass} mr-2`)
        .css({
            marginRight: '8px',
            fontSize: '1.1em'
        });
    
    // 添加内容
    const content = $('<div>')
        .css({
            display: 'flex',
            flexDirection: 'column'
        });
    
    const titleElem = $('<strong>')
        .text(title);
        
    const msgElem = $('<span>')
        .text(message);
        
    content.append(titleElem);
    content.append(msgElem);
    
    // 添加关闭按钮
    const closeBtn = $('<button>')
        .addClass('close')
        .html('&times;')
        .css({
            marginLeft: '10px',
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            fontSize: '1.2em',
            padding: '0',
            lineHeight: '0.5'
        })
        .on('click', function() {
            infoDiv.fadeOut(300, function() {
                $(this).remove();
            });
        });
    
    // 组装提示
    infoDiv.append(icon);
    infoDiv.append(content);
    infoDiv.append(closeBtn);
    
    // 添加到容器
    $('#dagContainer').append(infoDiv);
    
    // 显示提示
    setTimeout(() => {
        infoDiv.css('opacity', 1);
    }, 10);
    
    // 自动淡出
    setTimeout(() => {
        infoDiv.fadeOut(500, function() {
            $(this).remove();
        });
    }, 4000);
};

/**
 * 添加拖拽操作的视觉反馈和监听器
 */

// 添加拖拽操作结束时的视觉反馈和自动保存
window.applyDragVisualFeedback = function() {
    // 添加CSS动画效果
    if (!$('#dragAnimationStyle').length) {
        const styleElement = $('<style id="dragAnimationStyle">')
            .html(`
                @keyframes nodePositionSaved {
                    0% { filter: drop-shadow(0 0 5px rgba(40, 167, 69, 0.8)); }
                    100% { filter: none; }
                }
                .node-position-saved {
                    animation: nodePositionSaved 1.5s ease-out forwards;
                }
            `);
        $('head').append(styleElement);
    }
    
    // 监听应用更改按钮
    $('#applyChangesBtn').on('click', function() {
        // 应用图形模式的更改
        if (window.applyGraphModeChanges) {
            window.applyGraphModeChanges();
            
            // 视觉反馈 - 高亮应用更改成功的节点
            $('#dagContainer svg').find('g.node-group').each(function() {
                $(this).addClass('node-position-saved');
                
                // 5秒后移除动画类
                setTimeout(() => {
                    $(this).removeClass('node-position-saved');
                }, 1500);
            });
            
            // 成功信息
            window.showFloatingInfo('已保存', '节点位置已成功保存', 'success');
        }
    });
    
    // 添加右键菜单功能
    $('#dagContainer').on('contextmenu', function(e) {
        // 阻止默认右键菜单
        e.preventDefault();
        
        // 如果不是图形模式，不做任何处理
        if (!window.graphModeEnabled) return;
        
        // 检查是否已有上下文菜单
        $('.workflow-context-menu').remove();
        
        // 创建上下文菜单
        const contextMenu = $('<div>')
            .addClass('workflow-context-menu')
            .css({
                position: 'absolute',
                top: e.pageY - $(this).offset().top + $(this).scrollTop(),
                left: e.pageX - $(this).offset().left + $(this).scrollLeft(),
                background: 'white',
                border: '1px solid #ccc',
                borderRadius: '4px',
                boxShadow: '0 2px 5px rgba(0,0,0,0.2)',
                zIndex: 1000,
                padding: '5px 0'
            });
            
        // 菜单项 - 应用更改
        const applyItem = $('<div>')
            .addClass('context-menu-item')
            .css({
                padding: '6px 15px',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
                fontSize: '0.9rem'
            })
            .text('应用位置更改')
            .hover(
                function() { $(this).css('background', '#f0f0f0'); },
                function() { $(this).css('background', ''); }
            )
            .on('click', function() {
                window.applyGraphModeChanges();
                $('.workflow-context-menu').remove();
            });
            
        // 菜单项 - 添加节点
        const addNodeItem = $('<div>')
            .addClass('context-menu-item')
            .css({
                padding: '6px 15px',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
                fontSize: '0.9rem'
            })
            .text('在此处添加节点')
            .hover(
                function() { $(this).css('background', '#f0f0f0'); },
                function() { $(this).css('background', ''); }
            )
            .on('click', function() {
                // 弹出节点类型选择
                const svgOffset = $('#dagContainer svg').offset();
                const containerOffset = $('#dagContainer').offset();
                const clickX = e.pageX - containerOffset.left + $('#dagContainer').scrollLeft();
                const clickY = e.pageY - containerOffset.top + $('#dagContainer').scrollTop();
                
                addNodeAtPosition('process', clickX, clickY);
                $('.workflow-context-menu').remove();
            });
            
        // 菜单项 - 自动布局
        const autoLayoutItem = $('<div>')
            .addClass('context-menu-item')
            .css({
                padding: '6px 15px',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
                fontSize: '0.9rem'
            })
            .text('自动重排节点')
            .hover(
                function() { $(this).css('background', '#f0f0f0'); },
                function() { $(this).css('background', ''); }
            )
            .on('click', function() {
                $('#autoLayoutBtn').trigger('click');
                $('.workflow-context-menu').remove();
            });
            
        // 添加菜单项到菜单
        contextMenu.append(applyItem);
        contextMenu.append(addNodeItem);
        contextMenu.append(autoLayoutItem);
        
        // 添加分割线
        contextMenu.append($('<div>').css({
            height: '1px',
            background: '#ddd',
            margin: '5px 0'
        }));
        
        // 添加取消菜单项
        const cancelItem = $('<div>')
            .addClass('context-menu-item')
            .css({
                padding: '6px 15px',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
                fontSize: '0.9rem',
                color: '#777'
            })
            .text('取消')
            .hover(
                function() { $(this).css('background', '#f0f0f0'); },
                function() { $(this).css('background', ''); }
            )
            .on('click', function() {
                $('.workflow-context-menu').remove();
            });
            
        contextMenu.append(cancelItem);
        
        // 添加到容器
        $('#dagContainer').append(contextMenu);
        
        // 点击其他区域关闭菜单
        $(document).one('click', function() {
            $('.workflow-context-menu').remove();
        });
    });
};

// 在特定位置添加节点
function addNodeAtPosition(nodeType, posX, posY) {
    // 生成唯一ID
    const timestamp = new Date().getTime();
    const randomPart = Math.floor(Math.random() * 1000);
    
    // 创建节点对象
    let nodeId = 'node_' + timestamp % 10000;
    let nodeName = '处理节点';
    let parameters = {};
    
    switch(nodeType) {
        case 'process':
            nodeId = 'process_' + timestamp % 10000;
            nodeName = '处理节点';
            parameters = { 
                processType: 'standard',
                description: '执行标准处理'
            };
            break;
        default:
            nodeId = 'node_' + timestamp % 10000;
            nodeName = '普通节点';
    }
    
    // 读取现有节点
    let nodesArray = [];
    try {
        const nodesJson = $('#workflowNodesJson').val();
        nodesArray = nodesJson && nodesJson.trim() !== '' ? JSON.parse(nodesJson) : [];
    } catch(e) {
        console.error('解析节点JSON时出错:', e);
        return;
    }
    
    // 创建新节点对象
    const newNode = {
        nodeId: nodeId,
        nodeName: nodeName,
        parameters: parameters,
        position: {
            x: posX - 75, // 节点宽度的一半
            y: posY - 30  // 节点高度的一半
        }
    };
    
    // 添加到数组
    nodesArray.push(newNode);
    
    // 更新JSON
    $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
    
    // 重绘DAG
    redrawDAG();
    
    // 显示成功消息
    window.showFloatingInfo('节点已添加', `已在指定位置添加新节点: ${nodeId}`, 'success');
}

// 工作流安全检查工具
window.checkWorkflowSafetyState = function() {
    // 记录已执行的安全检查操作
    console.log("执行工作流状态安全检查");
    
    try {
        // 检查所有关键的全局变量状态
        if (window.selectedSourceElement === null && window.selectedSourceNodeId !== null) {
            console.warn("不一致的状态: selectedSourceNodeId 存在但 selectedSourceElement 为 null");
            window.selectedSourceNodeId = null;
        }
        
        // 检查另一种不一致的状态
        if (window.selectedSourceElement !== null && window.selectedSourceNodeId === null) {
            console.warn("不一致的状态: selectedSourceElement 存在但 selectedSourceNodeId 为 null");
            window.selectedSourceElement = null;
        }
        
        // 检查临时元素是否需要清理
        if (window.selectedSourceNodeId === null) {
            const tempElements = $('#tempAnimatedEdge, #tempEdgeAnimation, .node-action-status');
            if (tempElements.length > 0) {
                console.log(`清理 ${tempElements.length} 个临时元素`);
                $('#tempAnimatedEdge').remove();
                $('#tempEdgeAnimation').remove();
                $('.node-action-status').remove();
            }
        }
        
        // 确保没有节点保持高亮状态但没有被选中
        if (!window.selectedSourceNodeId) {
            $('#dagContainer svg g.node-group rect').each(function() {
                const fill = $(this).attr('fill');
                if (fill && fill !== '#fff' && !$(this).closest('.selected-node').length) {
                    console.log(`重置节点样式: ${$(this).closest('g.node-group').attr('data-node-id')}`);
                    $(this).attr('fill', '#fff')
                           .attr('stroke', '#007bff')
                           .attr('stroke-width', 2)
                           .css('filter', 'none');
                }
            });
        }
        
        // 检查边缘视觉指引
        if (window.selectedSourceNodeId === null && $('#edgeVisualGuide').length > 0) {
            console.log("清理未使用的边缘视觉指引");
            $('#edgeVisualGuide').remove();
            $('#dagContainer').off('mousemove.edgeGuide');
        }
        
        return true;
    } catch (e) {
        console.error("工作流状态安全检查出错:", e);
        return false;
    }
};

// 在显示模态窗口前进行安全检查
window.safeShowModal = function(modalId) {
    console.log(`安全显示模态窗口: ${modalId}`);
    
    try {
        // 首先关闭可能已经打开的其他模态窗口
        $('.modal').each(function() {
            const currentModalId = '#' + $(this).attr('id');
            if ($(this).hasClass('show') && currentModalId !== modalId) {
                console.log(`关闭已打开的模态窗口: ${currentModalId}`);
                $(currentModalId).modal('hide');
            }
        });
        
        // 在显示模态窗口前进行安全检查
        if (typeof window.checkWorkflowSafetyState === 'function') {
            const safetyCheckResult = window.checkWorkflowSafetyState();
            if (!safetyCheckResult) {
                console.warn(`${modalId} 安全检查失败，但仍继续显示模态窗口`);
            }
        }
        
        // 显示指定的模态窗口
        $(modalId).modal('show');
        console.log(`${modalId} 模态窗口已显示`);
        
        return true;
    } catch (e) {
        console.error(`显示模态窗口 ${modalId} 时出错:`, e);
        // 尝试恢复到安全状态
        window.selectedSourceNodeId = null;
        window.selectedSourceElement = null;
        $('#tempAnimatedEdge, #tempEdgeAnimation, .node-action-status, #edgeVisualGuide').remove();
        
        // 如果仍然需要显示模态窗口，使用原生方法
        try {
            $(modalId).modal('show');
            return true;
        } catch (e2) {
            console.error(`尝试恢复后显示模态窗口 ${modalId} 仍然失败:`, e2);
            return false;
        }
    }
};
