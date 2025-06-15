(function() {
    'use strict';

    let edgeCreationState = {
        isCreating: false,
        sourceNodeId: null,
        sourceSide: null,
        tempLine: null
    };

    window.initializeWorkflowEditor = function() {
        const nodesJsonInput = document.getElementById('workflowNodesJson');
        const edgesJsonInput = document.getElementById('workflowEdgesJson');

        const nodesJson = nodesJsonInput ? nodesJsonInput.value : '[]';
        const edgesJson = edgesJsonInput ? edgesJsonInput.value : '[]';

        if (window.NodeManager && typeof window.NodeManager.initializeEditor === 'function') {
            let dagContainer = document.getElementById('dagContainer');
            if (!dagContainer) {
                console.warn("'dagContainer' not found in the DOM. Workflow editor might not display correctly until it's available.");
            }
            window.NodeManager.initializeEditor('dagContainer', nodesJson, edgesJson);

            // Setup edge creation interaction
            setupEdgeCreationInteraction();
        } else {
            console.error("NodeManager or initializeEditor not found. Workflow editor cannot be initialized.");
        }
    };

    function setupEdgeCreationInteraction() {
        const dagContainer = document.getElementById('dagContainer');
        if (!dagContainer) {
            console.warn("DAG container not found for edge creation interaction.");
            return;
        }

        const svg = dagContainer.querySelector('svg');
        if (!svg) {
            console.warn("SVG element not found in DAG container.");
            return;
        }

        // Handle right-click + Ctrl on node to start edge creation (keep existing method)
        svg.addEventListener('mousedown', function(e) {
            const nodeGroup = findAncestorWithClass(e.target, 'workflow-node');
            if (nodeGroup && e.button === 2 && e.ctrlKey) { // Right mouse button + Ctrl
                e.preventDefault();
                const sourceNodeId = nodeGroup.getAttribute('data-node-id');
                if (sourceNodeId) {
                    startEdgeCreation(sourceNodeId, null, e, svg);
                }
            }
        });

        // Handle connection point mousedown to start edge creation
        svg.addEventListener('mousedown', function(e) {
            const connectionPoint = findAncestorWithClass(e.target, 'connection-point');
            if (connectionPoint && e.button === 0) { // Left mouse button
                e.preventDefault();
                const nodeGroup = findAncestorWithClass(connectionPoint, 'workflow-node');
                if (nodeGroup) {
                    const sourceNodeId = nodeGroup.getAttribute('data-node-id');
                    const sourceSide = connectionPoint.getAttribute('data-side');
                    if (sourceNodeId && sourceSide) {
                        startEdgeCreation(sourceNodeId, sourceSide, e, svg);
                    }
                }
            }
        });

        // Handle mousemove during edge creation
        svg.addEventListener('mousemove', function(e) {
            if (edgeCreationState.isCreating && edgeCreationState.tempLine) {
                updateTempLine(e, svg);
            }
        });

        // Handle click on node to complete edge creation
        svg.addEventListener('click', function(e) {
            if (edgeCreationState.isCreating) {
                const targetNodeGroup = findAncestorWithClass(e.target, 'workflow-node');
                if (targetNodeGroup) {
                    const targetNodeId = targetNodeGroup.getAttribute('data-node-id');
                    if (targetNodeId && targetNodeId !== edgeCreationState.sourceNodeId) {
                        if (window.NodeManager && typeof window.NodeManager.addEdge === 'function') {
                            const newEdge = window.NodeManager.addEdge(edgeCreationState.sourceNodeId, targetNodeId);
                            if (!newEdge) {
                                alert('Failed to create edge. It may already exist.');
                            }
                        }
                    } else if (targetNodeId === edgeCreationState.sourceNodeId) {
                        alert('Cannot create edge to the same node.');
                    }
                }
                endEdgeCreation();
            }
        });

        // Cancel edge creation when clicking outside nodes
        svg.addEventListener('click', function(e) {
            if (edgeCreationState.isCreating && !findAncestorWithClass(e.target, 'workflow-node')) {
                endEdgeCreation();
            }
        });

        // Prevent context menu during edge creation
        svg.addEventListener('contextmenu', function(e) {
            if (edgeCreationState.isCreating || findAncestorWithClass(e.target, 'workflow-node')) {
                e.preventDefault();
            }
        });
    }

    function startEdgeCreation(sourceNodeId, sourceSide, event, svg) {
        edgeCreationState.isCreating = true;
        edgeCreationState.sourceNodeId = sourceNodeId;
        edgeCreationState.sourceSide = sourceSide;

        // Create temporary line
        const tempLine = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        tempLine.setAttribute('stroke', '#666');
        tempLine.setAttribute('stroke-width', '2');
        tempLine.setAttribute('stroke-dasharray', '5,3');
        tempLine.setAttribute('fill', 'none');
        tempLine.setAttribute('marker-end', 'url(#arrowhead)');

        const sourceNode = window.NodeManager.getNodeById(sourceNodeId);
        if (sourceNode) {
            const CTM = svg.getScreenCTM().inverse();
            const mouseX = event.clientX * CTM.a + event.clientY * CTM.c + CTM.e;
            const mouseY = event.clientX * CTM.b + event.clientY * CTM.d + CTM.f;

            let startX = sourceNode.x + window.SVGManager.nodeWidth / 2;
            let startY = sourceNode.y + window.SVGManager.nodeHeight / 2;

            // Adjust startX/startY based on sourceSide
            switch (sourceSide) {
                case 'left':
                    startX = sourceNode.x;
                    startY = sourceNode.y + window.SVGManager.nodeHeight / 2;
                    break;
                case 'right':
                    startX = sourceNode.x + window.SVGManager.nodeWidth;
                    startY = sourceNode.y + window.SVGManager.nodeHeight / 2;
                    break;
                case 'top':
                    startX = sourceNode.x + window.SVGManager.nodeWidth / 2;
                    startY = sourceNode.y;
                    break;
                case 'bottom':
                    startX = sourceNode.x + window.SVGManager.nodeWidth / 2;
                    startY = sourceNode.y + window.SVGManager.nodeHeight;
                    break;
                default:
                    break;
            }

            tempLine.setAttribute('d', `M ${startX} ${startY} L ${mouseX} ${mouseY}`);
            svg.appendChild(tempLine);
            edgeCreationState.tempLine = tempLine;
        }
    }

    function updateTempLine(event, svg) {
        if (!edgeCreationState.tempLine) return;

        const sourceNode = window.NodeManager.getNodeById(edgeCreationState.sourceNodeId);
        if (sourceNode) {
            const CTM = svg.getScreenCTM().inverse();
            const mouseX = event.clientX * CTM.a + event.clientY * CTM.c + CTM.e;
            const mouseY = event.clientX * CTM.b + event.clientY * CTM.d + CTM.f;

            let startX = sourceNode.x + window.SVGManager.nodeWidth / 2;
            let startY = sourceNode.y + window.SVGManager.nodeHeight / 2;

            switch (edgeCreationState.sourceSide) {
                case 'left':
                    startX = sourceNode.x;
                    startY = sourceNode.y + window.SVGManager.nodeHeight / 2;
                    break;
                case 'right':
                    startX = sourceNode.x + window.SVGManager.nodeWidth;
                    startY = sourceNode.y + window.SVGManager.nodeHeight / 2;
                    break;
                case 'top':
                    startX = sourceNode.x + window.SVGManager.nodeWidth / 2;
                    startY = sourceNode.y;
                    break;
                case 'bottom':
                    startX = sourceNode.x + window.SVGManager.nodeWidth / 2;
                    startY = sourceNode.y + window.SVGManager.nodeHeight;
                    break;
                default:
                    break;
            }

            edgeCreationState.tempLine.setAttribute('d', `M ${startX} ${startY} L ${mouseX} ${mouseY}`);
        }
    }

    function endEdgeCreation() {
        if (edgeCreationState.tempLine && edgeCreationState.tempLine.parentNode) {
            edgeCreationState.tempLine.parentNode.removeChild(edgeCreationState.tempLine);
        }
        edgeCreationState.isCreating = false;
        edgeCreationState.sourceNodeId = null;
        edgeCreationState.sourceSide = null;
        edgeCreationState.tempLine = null;
    }

    function findAncestorWithClass(element, className) {
        while (element && element !== document) {
            if (element.classList && element.classList.contains(className)) {
                return element;
            }
            element = element.parentNode;
        }
        return null;
    }

    window.safeRedrawDAG = function(forceReloadFromJson = false) {
        if (!window.NodeManager) {
            console.error("NodeManager not found. Cannot redraw DAG.");
            return;
        }
        if (forceReloadFromJson) {
            const nodesJsonInput = document.getElementById('workflowNodesJson');
            const edgesJsonInput = document.getElementById('workflowEdgesJson');
            const nodesJson = nodesJsonInput ? nodesJsonInput.value : '[]';
            const edgesJson = edgesJsonInput ? edgesJsonInput.value : '[]';
            if (typeof window.NodeManager.loadWorkflow === 'function') {
                window.NodeManager.loadWorkflow(nodesJson, edgesJson);
            }
        } else {
            if (typeof window.NodeManager.renderDAG === 'function') {
                window.NodeManager.renderDAG();
            }
        }
    };

window.openNodeEditDialog = function(nodeId) {
    if (!window.NodeManager || !window.$) {
        console.error("NodeManager or jQuery not available for node edit dialog.");
        return;
    }
    const node = window.NodeManager.getNodeById(nodeId);
    if (node) {
        $('#editingNodeArrayIndex').val(node.id);
        $('#wfNodeId').val(node.id).prop('readonly', true);
        $('#wfNodeName').val(node.name);

        const taskSelector = $('#workflowNodeEditModal #wfNodeTaskConfigSelect');
        if (taskSelector && taskSelector.length) { // Ensure element exists
            taskSelector.val(node.taskConfigId || '');
        }

        try {
            $('#wfNodeParams').val(JSON.stringify(node.parameters || {}, null, 2));
        } catch (e) {
            $('#wfNodeParams').val('{}');
            console.error("Error stringifying node params for edit dialog:", e);
        }

        $('#workflowNodeEditModal').modal('show');
    } else {
        console.warn("Node not found for edit dialog:", nodeId);
    }
};

// Fix: Prevent edge edit modal from opening when clicking on nodes or their attachments
// Add event listener to prevent propagation of clicks on nodes that might trigger edge edit modal
$(document).on('click', '.workflow-node', function(event) {
    event.stopPropagation();
});


    window.saveWorkflowNode = function() {
        if (!window.NodeManager || !window.$) {
            console.error("NodeManager or jQuery not available for saving node.");
            return;
        }
        const nodeId = $('#editingNodeArrayIndex').val();
        const nodeName = $('#wfNodeName').val();
        const taskConfigId = $('#workflowNodeEditModal #wfNodeTaskConfigSelect').val();
        let nodeParams = {};
        try {
            const paramsString = $('#wfNodeParams').val();
            nodeParams = paramsString ? JSON.parse(paramsString) : {};
        } catch (e) {
            alert('Node parameters JSON is invalid: ' + e.message);
            return;
        }

        if (nodeId && nodeName) {
            window.NodeManager.updateNode(nodeId, {
                name: nodeName,
                taskConfigId: taskConfigId,
                parameters: nodeParams
            });
            $('#workflowNodeEditModal').modal('hide');
            window.NodeManager.renderDAG();
        } else {
            alert('Node ID is missing. Cannot save node.');
        }
    };

window.openEdgeEditDialog = function(edgeId = null) {
    console.log("Opening edge edit dialog for edgeId:", edgeId);
    if (!window.NodeManager || !window.$) {
        console.error("NodeManager or jQuery not available for edge edit dialog.");
        return;
    }
    const fromSelect = $('#wfEdgeFrom');
    const toSelect = $('#wfEdgeTo');

    fromSelect.empty().append($('<option>', { value: '', text: '-- Select Source --' }));
    toSelect.empty().append($('<option>', { value: '', text: '-- Select Target --' }));

    if (window.NodeManager.nodes && Array.isArray(window.NodeManager.nodes)) {
        window.NodeManager.nodes.forEach(function(node) {
            fromSelect.append($('<option>', { value: node.id, text: `${node.name || node.id} (${node.id})` }));
            toSelect.append($('<option>', { value: node.id, text: `${node.name || node.id} (${node.id})` }));
        });
    }

    if (edgeId) {
        const edge = window.NodeManager.edges.find(function(e) { return e.id === edgeId; });
        if (edge) {
            $('#editingEdgeArrayIndex').val(edge.id);
            fromSelect.val(edge.fromNodeId);
            toSelect.val(edge.toNodeId);
            $('#wfEdgeExpression').val(edge.expression || '');
            $('#wfEdgePriority').val(edge.priority || 1);
            $('#deleteEdgeBtn').show();
        } else {
            console.warn("Edge not found for edit dialog:", edgeId);
            $('#editingEdgeArrayIndex').val('');
            $('#wfEdgeExpression').val('');
            $('#wfEdgePriority').val(1);
            $('#deleteEdgeBtn').hide();
        }
    } else {
        $('#editingEdgeArrayIndex').val('');
        $('#wfEdgeExpression').val('');
        $('#wfEdgePriority').val(1);
        $('#deleteEdgeBtn').hide();
    }
    $('#workflowEdgeEditModal').modal('show');
};


    window.saveWorkflowEdge = function() {
        if (!window.NodeManager || !window.$) {
            console.error("NodeManager or jQuery not available for saving edge.");
            return;
        }
        const edgeId = $('#editingEdgeArrayIndex').val();
        const fromNodeId = $('#wfEdgeFrom').val();
        const toNodeId = $('#wfEdgeTo').val();
        const expression = $('#wfEdgeExpression').val();
        const priority = parseInt($('#wfEdgePriority').val()) || 1;

        if (!fromNodeId || !toNodeId) {
            alert('Source and Target nodes must be selected.');
            return;
        }
        if (fromNodeId === toNodeId) {
            alert('Source and Target nodes cannot be the same.');
            return;
        }

        if (edgeId) {
            window.NodeManager.updateEdge(edgeId, {
                fromNodeId: fromNodeId,
                toNodeId: toNodeId,
                expression: expression,
                priority: priority
            });
        } else {
            window.NodeManager.addEdge(fromNodeId, toNodeId, expression, priority);
        }
        $('#workflowEdgeEditModal').modal('hide');
    };

    window.deleteWorkflowEdge = function() {
        if (!window.NodeManager || !window.$) {
            console.error("NodeManager or jQuery not available for deleting edge.");
            return;
        }
        const edgeId = $('#editingEdgeArrayIndex').val();
        if (edgeId) {
            if (confirm('Are you sure you want to delete this edge?')) {
                window.NodeManager.deleteEdge(edgeId);
                $('#workflowEdgeEditModal').modal('hide');
            }
        } else {
            alert('No edge selected for deletion.');
        }
    };

})();
