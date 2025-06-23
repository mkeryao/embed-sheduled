(function() {
    'use strict';

    const svgNS = 'http://www.w3.org/2000/svg';
    let dagSvg = null; 
    let dagContainerElement = null;
    let edgeStartNode = null;
    let edgeStartPoint = null;
    let tempEdgePath = null;
    let potentialTargetNode = null;
    let potentialTargetPoint = null;

    const SVGManager = {
        nodeWidth: 150,
        nodeHeight: 60,
        nextNodeX: 50,
        nextNodeY: 50,
        tooltip: null,

        showTooltip: function(text, x, y) {
            if (!this.tooltip) {
                this.tooltip = document.createElement('div');
                this.tooltip.style.position = 'fixed';
                this.tooltip.style.padding = '5px 10px';
                this.tooltip.style.background = 'rgba(0,0,0,0.8)';
                this.tooltip.style.color = 'white';
                this.tooltip.style.borderRadius = '4px';
                this.tooltip.style.fontSize = '12px';
                this.tooltip.style.pointerEvents = 'none';
                this.tooltip.style.zIndex = '1000';
                document.body.appendChild(this.tooltip);
            }
            this.tooltip.textContent = text;
            this.tooltip.style.left = x + 'px';
            this.tooltip.style.top = (y - 30) + 'px';
            this.tooltip.style.display = 'block';
        },

        hideTooltip: function() {
            if (this.tooltip) {
                this.tooltip.style.display = 'none';
            }
        },

        initializeSvg: function(containerElementId) {
            dagContainerElement = document.getElementById(containerElementId);
            if (!dagContainerElement) {
                console.error(`DAG container #${containerElementId} not found.`);
                return null;
            }

            // Clear previous SVG if any
            while (dagContainerElement.firstChild) {
                dagContainerElement.removeChild(dagContainerElement.firstChild);
            }

            const svg = document.createElementNS(svgNS, 'svg');
            svg.setAttribute('width', '100%'); 
            svg.setAttribute('height', '500px'); 
            svg.setAttribute('class', 'workflow-dag-svg');
            
            const initialWidth = dagContainerElement.clientWidth || 1200;
            const initialHeight = 500;
            svg.setAttribute('viewBox', `0 0 ${initialWidth} ${initialHeight}`); 
            
            dagContainerElement.appendChild(svg);
            dagSvg = svg;

            // Define arrowhead marker
            const defs = document.createElementNS(svgNS, 'defs');
            const marker = document.createElementNS(svgNS, 'marker');
            marker.setAttribute('id', 'arrowhead');
            marker.setAttribute('viewBox', '0 0 10 10');
            marker.setAttribute('refX', '9'); 
            marker.setAttribute('refY', '5');
            marker.setAttribute('markerWidth', '6');
            marker.setAttribute('markerHeight', '6');
            marker.setAttribute('orient', 'auto-start-reverse'); 

            const markerPath = document.createElementNS(svgNS, 'path');
            markerPath.setAttribute('d', 'M 0 0 L 10 5 L 0 10 z');
            markerPath.setAttribute('fill', '#555');
            
            marker.appendChild(markerPath);
            defs.appendChild(marker);
            svg.appendChild(defs);

            // Add mousemove listener for edge creation
            svg.addEventListener('mousemove', (e) => {
                if (edgeStartNode && tempEdgePath) {
                    const CTM = svg.getScreenCTM().inverse();
                    const mouseX = e.clientX * CTM.a + e.clientY * CTM.c + CTM.e;
                    const mouseY = e.clientX * CTM.b + e.clientY * CTM.d + CTM.f;
                    
                    // If hovering over a potential target node's connection point, use that point
                    // Otherwise, use mouse position
                    const endPos = potentialTargetPoint ? 
                        this.getConnectionPointPosition(potentialTargetNode, potentialTargetPoint) :
                        { x: mouseX, y: mouseY };
                    
                    const startPos = this.getConnectionPointPosition(edgeStartNode, edgeStartPoint);
                    const pathData = `M ${startPos.x} ${startPos.y} C ${startPos.x + 50} ${startPos.y}, ${endPos.x - 50} ${endPos.y}, ${endPos.x} ${endPos.y}`;
                    tempEdgePath.setAttribute('d', pathData);

                    // Show tooltip with instructions
                    if (potentialTargetPoint) {
                        this.showTooltip('Release to connect nodes', e.clientX + 10, e.clientY);
                    } else {
                        this.showTooltip('Drag to a connection point (+) on target node', e.clientX + 10, e.clientY);
                    }
                }
            });

            // Add mouseup handler to SVG for canceling edge creation
            svg.addEventListener('mouseup', (e) => {
                if (edgeStartNode) {
                    if (!potentialTargetNode || !potentialTargetPoint) {
                        console.log('Cancelling edge creation, no valid target');
                        this.resetEdgeCreation();
                    }
                }
                this.hideTooltip();
            });
            
            console.log('SVG initialized in', containerElementId);
            return svg;
        },

        getConnectionPointPosition: function(node, side) {
            if (!node || !side) return null;
            
            const x = node.x + (side === 'right' ? this.nodeWidth : 
                              side === 'left' ? 0 : 
                              this.nodeWidth / 2);
            
            const y = node.y + (side === 'bottom' ? this.nodeHeight :
                              side === 'top' ? 0 :
                              this.nodeHeight / 2);
            
            return { x, y };
        },

        findClosestConnectionPoint: function(node, mouseX, mouseY) {
            const points = [
                { side: 'left', x: node.x, y: node.y + this.nodeHeight/2 },
                { side: 'right', x: node.x + this.nodeWidth, y: node.y + this.nodeHeight/2 },
                { side: 'top', x: node.x + this.nodeWidth/2, y: node.y },
                { side: 'bottom', x: node.x + this.nodeWidth/2, y: node.y + this.nodeHeight }
            ];

            let closest = points[0];
            let minDist = Number.MAX_VALUE;

            points.forEach(point => {
                const dist = Math.sqrt(
                    Math.pow(mouseX - point.x, 2) + 
                    Math.pow(mouseY - point.y, 2)
                );
                if (dist < minDist) {
                    minDist = dist;
                    closest = point;
                }
            });

            return closest.side;
        },

        createNodeElement: function(node) {
            const nodeGroup = document.createElementNS(svgNS, 'g');
            nodeGroup.setAttribute('class', `workflow-node node-${node.type || 'process'}`);
            nodeGroup.setAttribute('data-node-id', node.id);
            nodeGroup.setAttribute('transform', `translate(${node.x || 0},${node.y || 0})`);
            nodeGroup.style.cursor = 'move';

            const rect = document.createElementNS(svgNS, 'rect');
            rect.setAttribute('width', this.nodeWidth);
            rect.setAttribute('height', this.nodeHeight);
            rect.setAttribute('rx', '5');
            rect.setAttribute('ry', '5');
            rect.setAttribute('fill', '#fff');
            rect.setAttribute('stroke', '#666');
            rect.setAttribute('stroke-width', '2');

            const text = document.createElementNS(svgNS, 'text');
            text.setAttribute('x', this.nodeWidth / 2);
            text.setAttribute('y', this.nodeHeight / 2);
            text.setAttribute('text-anchor', 'middle');
            text.setAttribute('dominant-baseline', 'middle');
            text.setAttribute('fill', '#333');
            text.setAttribute('class', 'workflow-node-label');
            text.textContent = node.nodeName || node.id;

            nodeGroup.appendChild(rect);
            nodeGroup.appendChild(text);

            const connectionPoints = this.createConnectionPoints(node);
            connectionPoints.forEach(point => nodeGroup.appendChild(point));
            
            this.makeDraggable(nodeGroup, node);

            // Add hover handlers for showing/hiding connection points
            nodeGroup.addEventListener('mouseenter', () => {
                if (edgeStartNode !== node) {
                    connectionPoints.forEach(point => {
                        point.style.opacity = '1';
                    });
                }
            });

            nodeGroup.addEventListener('mouseleave', (e) => {
                // Don't hide points if we're creating an edge
                if (!edgeStartNode || (edgeStartNode === node && !this.isMouseOverNode(node, e))) {
                    connectionPoints.forEach(point => {
                        if (!point.classList.contains('active-connection')) {
                            point.style.opacity = '0';
                        }
                    });
                }
            });

            nodeGroup.addEventListener('dblclick', function() { 
                if (typeof window.openNodeEditDialog === 'function') {
                    window.openNodeEditDialog(node.id);
                }
            });

            return nodeGroup;
        },

        isMouseOverNode: function(node, event) {
            const rect = event.currentTarget.getBoundingClientRect();
            const x = event.clientX - rect.left;
            const y = event.clientY - rect.top;
            return x >= 0 && x <= this.nodeWidth && y >= 0 && y <= this.nodeHeight;
        },

        createConnectionPoints: function(node) {
            const points = [];
            const positions = [
                { x: 0, y: this.nodeHeight/2, side: 'left' },
                { x: this.nodeWidth, y: this.nodeHeight/2, side: 'right' },
                { x: this.nodeWidth/2, y: 0, side: 'top' },
                { x: this.nodeWidth/2, y: this.nodeHeight, side: 'bottom' }
            ];

            positions.forEach(pos => {
                const pointGroup = document.createElementNS(svgNS, 'g');
                pointGroup.setAttribute('class', 'connection-point');
                pointGroup.setAttribute('data-side', pos.side);
                pointGroup.setAttribute('transform', `translate(${pos.x},${pos.y})`);
                pointGroup.style.opacity = '0';
                pointGroup.style.cursor = 'pointer';

                const circle = document.createElementNS(svgNS, 'circle');
                circle.setAttribute('r', '6');
                circle.setAttribute('fill', '#fff');
                circle.setAttribute('stroke', '#666');
                circle.setAttribute('stroke-width', '2');

                const plus = document.createElementNS(svgNS, 'text');
                plus.setAttribute('x', '0');
                plus.setAttribute('y', '0');
                plus.setAttribute('text-anchor', 'middle');
                plus.setAttribute('dominant-baseline', 'middle');
                plus.setAttribute('fill', '#666');
                plus.textContent = '+';

                pointGroup.appendChild(circle);
                pointGroup.appendChild(plus);

                // Add mousedown handler for edge creation
                pointGroup.addEventListener('mousedown', (e) => {
                    e.stopPropagation();
                    if (!edgeStartNode) {
                        edgeStartNode = node;
                        edgeStartPoint = pos.side;
                        pointGroup.classList.add('active-connection');
                        document.body.style.cursor = 'crosshair';

                        // Create temporary edge path
                        tempEdgePath = document.createElementNS(svgNS, 'path');
                        tempEdgePath.setAttribute('stroke', '#666');
                        tempEdgePath.setAttribute('stroke-width', '2');
                        tempEdgePath.setAttribute('stroke-dasharray', '4');
                        tempEdgePath.setAttribute('fill', 'none');
                        tempEdgePath.setAttribute('marker-end', 'url(#arrowhead)');
                        dagSvg.appendChild(tempEdgePath);
                    }
                });

                // Add mouseenter handler for potential target
                pointGroup.addEventListener('mouseenter', (e) => {
                    if (edgeStartNode && edgeStartNode !== node) {
                        console.log('Potential target found:', node.id);
                        potentialTargetNode = node;
                        potentialTargetPoint = pos.side;
                        pointGroup.classList.add('potential-target');
                        circle.setAttribute('stroke', '#28a745');
                        plus.setAttribute('fill', '#28a745');
                    }
                });

                // Add mouseleave handler for potential target
                pointGroup.addEventListener('mouseleave', () => {
                    if (potentialTargetNode === node && !edgeStartNode) {
                        console.log('Left potential target:', node.id);
                        potentialTargetNode = null;
                        potentialTargetPoint = null;
                        pointGroup.classList.remove('potential-target');
                        circle.setAttribute('stroke', '#666');
                        plus.setAttribute('fill', '#666');
                    }
                });

                // Add mouseup handler for completing edge creation
                pointGroup.addEventListener('mouseup', (e) => {
                    e.stopPropagation();
                    if (edgeStartNode && edgeStartNode !== node) {
                        console.log('Completing edge creation from', edgeStartNode.id, 'to', node.id);
                        if (typeof window.NodeManager !== 'undefined' && 
                            typeof window.NodeManager.addEdge === 'function') {
                            window.NodeManager.addEdge(edgeStartNode.id, node.id);
                            // Only reset after edge is created
                            this.resetEdgeCreation();
                        }
                    }
                });

                points.push(pointGroup);
            });

            return points;
        },

        resetEdgeCreation: function() {
            if (tempEdgePath && tempEdgePath.parentNode) {
                tempEdgePath.parentNode.removeChild(tempEdgePath);
            }
            tempEdgePath = null;
            document.querySelector('.active-connection')?.classList.remove('active-connection');
            document.querySelector('.potential-target')?.classList.remove('potential-target');
            edgeStartNode = null;
            edgeStartPoint = null;
            potentialTargetNode = null;
            potentialTargetPoint = null;
            document.body.style.cursor = 'default';
        },
        
        makeDraggable: function(element, node) {
            let isDragging = false;
            let dragStartMousePos = { x: 0, y: 0 }; 
            let nodeStartPos = { x: 0, y: 0 };     

            element.addEventListener('mousedown', (e) => { 
                if (e.button !== 0) return; 
                if (!dagSvg) return; // Reverted to simpler check
                if (e.target.closest('.connection-point')) return;
                
                isDragging = true;
                
                const CTM = dagSvg.getScreenCTM().inverse();
                dragStartMousePos.x = e.clientX * CTM.a + e.clientY * CTM.c + CTM.e;
                dragStartMousePos.y = e.clientX * CTM.b + e.clientY * CTM.d + CTM.f;
                nodeStartPos.x = node.x;
                nodeStartPos.y = node.y;

                element.style.cursor = 'grabbing';
                dagSvg.style.cursor = 'grabbing'; 
                
                document.addEventListener('mousemove', onMouseMove);
                document.addEventListener('mouseup', onMouseUp);
            });

            const onMouseMove = (e) => { 
                if (isDragging) {
                    e.preventDefault();
                    if (!dagSvg) return; // Reverted to simpler check
                    const CTM = dagSvg.getScreenCTM().inverse();
                    const currentMouseX = e.clientX * CTM.a + e.clientY * CTM.c + CTM.e;
                    const currentMouseY = e.clientX * CTM.b + e.clientY * CTM.d + CTM.f; // Corrected formula
                    
                    node.x = nodeStartPos.x + (currentMouseX - dragStartMousePos.x);
                    node.y = nodeStartPos.y + (currentMouseY - dragStartMousePos.y);
                    
                    element.setAttribute('transform', `translate(${node.x},${node.y})`);
                    
                    // Debugging logs removed
                    if (typeof this.updateEdgesForNode === 'function') {
                        this.updateEdgesForNode(node.id); // Call SVGManager's own method
                    }
                    // Removed the "Force a manual refresh" block
                }
            };

            const onMouseUp = () => { 
                if (isDragging) {
                    isDragging = false;
                    element.style.cursor = 'move';
                    if (dagSvg) dagSvg.style.cursor = 'default';
                    
                    if (window.NodeManager && typeof window.NodeManager.serializeWorkflow === 'function') {
                        window.NodeManager.serializeWorkflow(); 
                    }
                    
                    document.removeEventListener('mousemove', onMouseMove);
                    document.removeEventListener('mouseup', onMouseUp);
                }
            };

            // Add mouseup handler to SVG for canceling edge creation
            dagSvg.addEventListener('mouseup', () => {
                if (edgeStartNode) {
                    this.resetEdgeCreation();
                }
            });
            // Removed the debug wrapper for updateEdgesForNode
        },

        getEdgePathData: function(sourceNode, targetNode) {
            if (!sourceNode || !targetNode) return "";
            
            const sourcePos = { x: sourceNode.x, y: sourceNode.y };
            const targetPos = { x: targetNode.x, y: targetNode.y };
            const anchors = this.calculateAnchors(sourcePos, targetPos);
            const sAnchor = anchors.start;
            const eAnchor = anchors.end;

            const dx = Math.abs(eAnchor.x - sAnchor.x);
            const dy = Math.abs(eAnchor.y - sAnchor.y);
            const distance = Math.sqrt(dx * dx + dy * dy);
            let controlLength = distance * 0.3; 
            controlLength = Math.min(100, Math.max(30, controlLength)); 

            let sControlX, sControlY, eControlX, eControlY;

            if (sAnchor.x === sourcePos.x) { sControlX = sAnchor.x - controlLength; sControlY = sAnchor.y; } 
            else if (sAnchor.x === sourcePos.x + this.nodeWidth) { sControlX = sAnchor.x + controlLength; sControlY = sAnchor.y; } 
            else if (sAnchor.y === sourcePos.y) { sControlX = sAnchor.x; sControlY = sAnchor.y - controlLength; } 
            else { sControlX = sAnchor.x; sControlY = sAnchor.y + controlLength; }

            if (eAnchor.x === targetPos.x) { eControlX = eAnchor.x - controlLength; eControlY = eAnchor.y; } 
            else if (eAnchor.x === targetPos.x + this.nodeWidth) { eControlX = eAnchor.x + controlLength; eControlY = eAnchor.y; } 
            else if (eAnchor.y === targetPos.y) { eControlX = eAnchor.x; eControlY = eAnchor.y - controlLength; } 
            else { eControlX = eAnchor.x; eControlY = eAnchor.y + controlLength; }
            
            if (Math.abs(sAnchor.x - eAnchor.x) < this.nodeWidth * 0.5) { 
                sControlX = sAnchor.x + (sAnchor.x < eAnchor.x ? controlLength * 0.5 : -controlLength * 0.5);
                eControlX = eAnchor.x + (eAnchor.x < sAnchor.x ? controlLength * 0.5 : -controlLength * 0.5);
            }
            if (Math.abs(sAnchor.y - eAnchor.y) < this.nodeHeight * 0.5) {
                sControlY = sAnchor.y + (sAnchor.y < eAnchor.y ? controlLength * 0.5 : -controlLength * 0.5);
                eControlY = eAnchor.y + (eAnchor.y < sAnchor.y ? controlLength * 0.5 : -controlLength * 0.5);
            }

            return `M ${sAnchor.x} ${sAnchor.y} C ${sControlX} ${sControlY}, ${eControlX} ${eControlY}, ${eAnchor.x} ${eAnchor.y}`;
        },

        calculateAnchors: function(sourcePos, targetPos) {
            const sCenterX = sourcePos.x + this.nodeWidth / 2;
            const sCenterY = sourcePos.y + this.nodeHeight / 2;
            const tCenterX = targetPos.x + this.nodeWidth / 2;
            const tCenterY = targetPos.y + this.nodeHeight / 2;

            const deltaX = tCenterX - sCenterX;
            const deltaY = tCenterY - sCenterY;
            
            // Always connect to the center of the sides
            let startAnchor = { x: sCenterX, y: sCenterY };
            let endAnchor = { x: tCenterX, y: tCenterY };

            // Determine which side to connect based on relative positions
            if (Math.abs(deltaX) > Math.abs(deltaY)) {
                // Connect to left or right sides
                startAnchor.x = deltaX > 0 ? sourcePos.x + this.nodeWidth : sourcePos.x;
                startAnchor.y = sourcePos.y + this.nodeHeight / 2;
                
                endAnchor.x = deltaX > 0 ? targetPos.x : targetPos.x + this.nodeWidth;
                endAnchor.y = targetPos.y + this.nodeHeight / 2;
            } else {
                // Connect to top or bottom sides
                startAnchor.x = sourcePos.x + this.nodeWidth / 2;
                startAnchor.y = deltaY > 0 ? sourcePos.y + this.nodeHeight : sourcePos.y;
                
                endAnchor.x = targetPos.x + this.nodeWidth / 2;
                endAnchor.y = deltaY > 0 ? targetPos.y : targetPos.y + this.nodeHeight;
            }

            return { start: startAnchor, end: endAnchor };
        },

        createEdgeElement: function(edge, sourceNode, targetNode) {
            const path = document.createElementNS(svgNS, 'path');
            const pathData = this.getEdgePathData(sourceNode, targetNode);
            
            path.setAttribute('d', pathData);
            path.setAttribute('stroke', edge.expression ? '#28a745' : '#555'); 
            path.setAttribute('stroke-width', '2');
            path.setAttribute('fill', 'none');
            path.setAttribute('marker-end', 'url(#arrowhead)');
            path.setAttribute('class', `workflow-edge${edge.expression ? ' conditional' : ''}`);
            path.setAttribute('data-from-node', edge.fromNodeId);
            path.setAttribute('data-to-node', edge.toNodeId);
            if (edge.id) path.setAttribute('data-edge-id', edge.id);

            // Add edit icon in the middle of the edge
            const edgeGroup = document.createElementNS(svgNS, 'g');
            edgeGroup.setAttribute('class', 'edge-group');
            edgeGroup.appendChild(path);

            // Calculate middle point of the path
            const pathLength = path.getTotalLength();
            const midPoint = path.getPointAtLength(pathLength / 2);

            // Create edit icon circle background
            const iconCircle = document.createElementNS(svgNS, 'circle');
            iconCircle.setAttribute('cx', midPoint.x);
            iconCircle.setAttribute('cy', midPoint.y);
            iconCircle.setAttribute('r', '8');
            iconCircle.setAttribute('fill', '#fff');
            iconCircle.setAttribute('stroke', '#666');
            iconCircle.setAttribute('class', 'edge-edit-icon');
            iconCircle.style.cursor = 'pointer';

            // Create edit icon (pencil symbol)
            const iconText = document.createElementNS(svgNS, 'text');
            iconText.setAttribute('x', midPoint.x);
            iconText.setAttribute('y', midPoint.y);
            iconText.setAttribute('text-anchor', 'middle');
            iconText.setAttribute('dominant-baseline', 'middle');
            iconText.setAttribute('fill', '#666');
            iconText.setAttribute('font-size', '12px');
            iconText.textContent = '✎';
            iconText.style.cursor = 'pointer';
            iconText.style.pointerEvents = 'none';

            // Add click handler for edit icon
            iconCircle.addEventListener('click', function(e) {
                console.log('Edge icon circle clicked, edge id:', edge.id);
                e.stopPropagation();
                if (typeof window.openEdgeEditDialog === 'function') {
                    window.openEdgeEditDialog(edge.id);
                }
            });
            // Add click handler for edit icon text to also open edge edit dialog
            iconText.style.pointerEvents = 'auto'; // Enable pointer events for text
            iconText.addEventListener('click', function(e) {
                console.log('Edge icon text clicked, edge id:', edge.id);
                e.stopPropagation();
                e.preventDefault();
                if (typeof window.openEdgeEditDialog === 'function') {
                    setTimeout(() => {
                        window.openEdgeEditDialog(edge.id);
                    }, 0);
                }
            });
            iconText.style.pointerEvents = 'none';

            edgeGroup.appendChild(iconCircle);
            edgeGroup.appendChild(iconText);

            return edgeGroup;
        },

        updateEdgesForNode: function(nodeId) {
            // Debugging logs removed
            const currentDagSvg = typeof window.getDagSvg === 'function' ? window.getDagSvg() : null;
            
            if (!currentDagSvg || !window.SVGManager) {
                // console.warn('SVG or SVGManager not found, exiting updateEdgesForNode.'); // Kept as a useful warning if needed later
                return;
            }

            const self = this;
            const edgeGroups = currentDagSvg.querySelectorAll('.edge-group');

            edgeGroups.forEach(function(edgeGroup) {
                const edgeElement = edgeGroup.querySelector('.workflow-edge');
                if (!edgeElement) {
                    return;
                }

                const fromId = edgeElement.getAttribute('data-from-node');
                const toId = edgeElement.getAttribute('data-to-node');

                if (fromId !== nodeId && toId !== nodeId) return;

                const sourceNodeData = window.NodeManager.getNodeById(fromId); // Assuming window.NodeManager is the data provider
                const targetNodeData = window.NodeManager.getNodeById(toId); // Assuming window.NodeManager is the data provider

                if (sourceNodeData && targetNodeData) {
                    // Update path data
                    const pathData = self.getEdgePathData(sourceNodeData, targetNodeData);
                    edgeElement.setAttribute('d', pathData);

                    // Calculate new middle point for edit icon
                    const pathLength = edgeElement.getTotalLength();
                    const midPoint = edgeElement.getPointAtLength(pathLength / 2);

                    // Update edit icon position
                    const iconCircle = edgeGroup.querySelector('.edge-edit-icon');
                    const iconText = edgeGroup.querySelector('text'); 
                    if (iconCircle && iconText) {
                        iconCircle.setAttribute('cx', midPoint.x);
                        iconCircle.setAttribute('cy', midPoint.y);
                        iconText.setAttribute('x', midPoint.x);
                        iconText.setAttribute('y', midPoint.y);
                    }
                }
            });
        }



    }; 

    window.SVGManager = SVGManager;
    // window.NodeManager = SVGManager; // Removed as it's redundant and potentially confusing
    window.getDagSvg = function() { return dagSvg; };
    window.getDagContainerElement = function() { return dagContainerElement; };

})();
