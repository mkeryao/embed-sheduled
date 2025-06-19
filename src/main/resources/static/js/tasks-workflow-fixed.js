(function() {
    'use strict';

    const svgNS = 'http://www.w3.org/2000/svg';
    let dagSvg = null; 
    let dagContainerElement = null; 

    const SVGManager = {
        nodeWidth: 150,
        nodeHeight: 60,
        nextNodeX: 50,
        nextNodeY: 50,

        initializeSvg: function(containerElementId) {
            dagContainerElement = document.getElementById(containerElementId);
            if (!dagContainerElement) {
                console.error(`DAG container #${containerElementId} not found.`);
                return null;
            }

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
            
            console.log('SVG initialized in', containerElementId);
            return svg;
        },

        // Implement openEdgeEditDialog to open the modal and populate fields
        openEdgeEditDialog: function(edgeId) {
            if (!window.NodeManager) {
                console.error('NodeManager is not defined.');
                return;
            }
            const edge = window.NodeManager.getEdgeById(edgeId);
            if (!edge) {
                console.error('Edge not found with id:', edgeId);
                return;
            }
            // Set hidden input for editing edge index or id
            $('#editingEdgeArrayIndex').val(edgeId);

            // Populate modal fields
            $('#wfEdgeFrom').val(edge.fromNodeId);
            $('#wfEdgeTo').val(edge.toNodeId);
            $('#wfEdgeExpression').val(edge.expression || '');
            $('#wfEdgePriority').val(edge.priority || 1);

            // Show the modal
            $('#workflowEdgeEditModal').modal('show');
        },

        // Save edge changes from modal
        saveWorkflowEdge: function() {
            if (!window.NodeManager) {
                console.error('NodeManager is not defined.');
                return;
            }
            const edgeId = $('#editingEdgeArrayIndex').val();
            if (!edgeId) {
                alert('No edge selected for saving.');
                return;
            }
            const fromNodeId = $('#wfEdgeFrom').val();
            const toNodeId = $('#wfEdgeTo').val();
            const expression = $('#wfEdgeExpression').val();
            const priority = parseInt($('#wfEdgePriority').val(), 10) || 1;

            // Validate from and to nodes
            if (!fromNodeId || !toNodeId) {
                alert('From Node and To Node must be selected.');
                return;
            }

            // Update edge in NodeManager
            const success = window.NodeManager.updateEdge(edgeId, {
                fromNodeId: fromNodeId,
                toNodeId: toNodeId,
                expression: expression,
                priority: priority
            });

            if (success) {
                $('#workflowEdgeEditModal').modal('hide');
                window.safeRedrawDAG(true);
            } else {
                alert('Failed to update edge.');
            }
        },

        // Delete edge from modal
        deleteWorkflowEdge: function() {
            if (!window.NodeManager) {
                console.error('NodeManager is not defined.');
                return;
            }
            const edgeId = $('#editingEdgeArrayIndex').val();
            if (!edgeId) {
                alert('No edge selected for deletion.');
                return;
            }
            if (confirm('Are you sure you want to delete this edge?')) {
                const success = window.NodeManager.deleteEdge(edgeId);
                if (success) {
                    $('#workflowEdgeEditModal').modal('hide');
                    window.safeRedrawDAG(true);
                } else {
                    alert('Failed to delete edge.');
                }
            }
        }
    };

    // Expose functions globally for modal buttons
    window.openEdgeEditDialog = function(edgeId) {
        SVGManager.openEdgeEditDialog(edgeId);
    };
    window.saveWorkflowEdge = function() {
        SVGManager.saveWorkflowEdge();
    };
    window.deleteWorkflowEdge = function() {
        SVGManager.deleteWorkflowEdge();
    };

    window.SVGManager = SVGManager;

})();
