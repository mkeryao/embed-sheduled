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
    }

    /**
     * 重绘DAG图的核心函数
     * @private
     */
    function performRedrawDAG(enableDragging) {
        let safetyTimeout;
        try {
            window.isRedrawingDAG = true;
            
            safetyTimeout = setTimeout(function() {
                console.warn('重绘DAG超时，重置状态');
                window.isRedrawingDAG = false;
                window.redrawDAGCounter = Math.max(0, window.redrawDAGCounter - 1);
            }, 10000);
            
            const dagContainer = $('#dagContainer');
            const nodesJson = $('#workflowNodesJson').val();
            
            if (!nodesJson || nodesJson.trim() === '') {
                dagContainer.html('<div class="text-muted">没有节点数据可显示。点击"添加节点"开始创建工作流。</div>');
                return;
            }
            
            let nodes;
            try {
                nodes = JSON.parse(nodesJson);
                console.log(`解析出${nodes.length}个节点`);
            } catch (e) {
                console.error('解析节点JSON失败:', e);
                dagContainer.html(`<div class="alert alert-danger">解析工作流节点数据失败: ${e.message}</div>`);
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
                const x = node.x || 100 + index * 200;
                const y = node.y || 100;

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
                let selectedNode = null;
                let offset = { x: 0, y: 0 };

                svg.find('.workflow-node').on('mousedown', function(e) {
                    if (e.which === 1) {
                        selectedNode = this;
                        const transform = this.getAttribute('transform');
                        const match = /translate\(([^,]+),([^)]+)\)/.exec(transform);
                        if (match) {
                            const pos = { x: parseFloat(match[1]), y: parseFloat(match[2]) };
                            offset.x = e.clientX - pos.x;
                            offset.y = e.clientY - pos.y;
                        }
                        e.preventDefault();
                    }
                });

                $(document).on('mousemove', function(e) {
                    if (selectedNode) {
                        const newX = e.clientX - offset.x;
                        const newY = e.clientY - offset.y;
                        selectedNode.setAttribute('transform', `translate(${newX},${newY})`);
                        
                        const nodeId = $(selectedNode).data('node-id');
                        window.nodePositionsMap[nodeId] = { x: newX, y: newY };
                        window.drawWorkflowEdges(svg[0], window.nodePositionsMap);
                    }
                });

                $(document).on('mouseup', function() {
                    selectedNode = null;
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
     * 安全的重绘DAG函数，用于公开API调用
     */
    window.safeRedrawDAG = function(enableDragging) {
        try {
            window.redrawDAG(enableDragging);
        } catch (e) {
            console.error('安全重绘DAG时发生错误:', e);
            window.isRedrawingDAG = false;
            window.redrawDAGCounter = 0;
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
     */
    window.addWorkflowNode = function(nodeData) {
        try {
            let nodes = [];
            const nodesJson = $('#workflowNodesJson').val();
            if (nodesJson && nodesJson.trim() !== '') {
                nodes = JSON.parse(nodesJson);
            }
            
            if (nodes.some(node => node.id === nodeData.id)) {
                throw new Error('节点ID已存在');
            }
            
            nodes.push(nodeData);
            $('#workflowNodesJson').val(JSON.stringify(nodes, null, 2));
            window.safeRedrawDAG(true);
            return true;
        } catch (e) {
            console.error('添加工作流节点失败:', e);
            return false;
        }
    };

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

    window.deleteWorkflowEdge = function(fromNode, toNode) {
        try {
            let edges = JSON.parse($('#workflowEdgesJson').val());
            edges = edges.filter(edge => 
                !(edge.fromNode === fromNode && edge.toNode === toNode)
            );
            
            $('#workflowEdgesJson').val(JSON.stringify(edges, null, 2));
            window.safeRedrawDAG(true);
            return true;
        } catch (e) {
            console.error('删除工作流边缘失败:', e);
            return false;
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
