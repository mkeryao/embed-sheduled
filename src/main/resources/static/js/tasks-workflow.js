function drawWorkflowInstanceStatusDAG(svgContainer, nodes, edges, nodeStatuses) {
    svgContainer.empty();
    if (!nodes || nodes.length === 0) {
        svgContainer.append(`<p>${i18n.translate('logsPage.workflowInstanceModal.noNodesDefined', 'No nodes defined for this workflow.')}</p>`);
        i18n.applyTranslations(); // Apply to this message
        return;
    }

    const statusMap = new Map((nodeStatuses || []).map(s => [s.nodeId, s]));
    const svgNS = 'http://www.w3.org/2000/svg';
    const svg = $(document.createElementNS(svgNS, 'svg'));
    svg.attr('width', '100%'); // Height will be dynamic

    // Define arrowhead marker (unique ID for this SVG instance if needed, e.g., #arrowheadVisLogs)
    const arrowheadId = 'arrowheadVisLogs';
    if (svg.find('#' + arrowheadId).length === 0) {
       const defs = $(document.createElementNS(svgNS, 'defs'));
       const marker = $(document.createElementNS(svgNS, 'marker'));
       marker.attr('id', arrowheadId).attr('viewBox', '-0 -5 10 10').attr('refX', 8).attr('refY', 0)
             .attr('orient', 'auto').attr('markerWidth', 6).attr('markerHeight', 6).attr('overflow', 'visible');
       // Using a neutral color for arrows here, can be themed
       marker.append($(document.createElementNS(svgNS, 'path')).attr('d', 'M 0,-5 L 10 ,0 L 0,5').attr('fill', '#555'));
       defs.append(marker);
       svg.prepend(defs);
    }

    const nodeWidth = 160, nodeHeight = 65, nodeVMargin = 25, nodeHMargin = 20; // Adjusted for more info
    let currentY = nodeVMargin;
    const nodePositions = {}; // Store { x_center, y_center }

    (nodes || []).forEach(function(node) {
        const g = $(document.createElementNS(svgNS, 'g')).addClass('dag-node-instance vis-node'); // Added vis-node class
        const nodeStatusInfo = statusMap.get(node.nodeId) || { status: 'NOT_EXECUTE', nodeId: node.nodeId };

        let fillColor = '#f8f9fa'; // Default (light gray)
        let strokeColor = '#6c757d'; // Default border (gray)
        let textColor = '#212529'; // Default text (dark)

        switch ((nodeStatusInfo.status || '').toUpperCase()) {
            case 'SUCCESS': fillColor = '#d1e7dd'; strokeColor = '#198754'; textColor = '#0f5132'; break;
            case 'FAILED': case 'TIMED_OUT': fillColor = '#f8d7da'; strokeColor = '#dc3545'; textColor = '#842029'; break;
            case 'RUNNING': fillColor = '#cce5ff'; strokeColor = '#0d6efd'; textColor = '#052c65'; break;
            case 'SKIPPED': case 'NOT_EXECUTED': fillColor = '#e9ecef'; strokeColor = '#adb5bd'; textColor = '#495057'; break;
            case 'NOT_EXECUTE': fillColor = '#f0f0f0'; strokeColor = '#ced4da'; textColor = '#6c757d'; break;
        }

        const rect = $(document.createElementNS(svgNS, 'rect'))
            .attr({ x: 0, y: 0, width: nodeWidth, height: nodeHeight, fill: fillColor, stroke: strokeColor, 'stroke-width': 1.5, rx: 6 });
        g.append(rect);

        const textNodeName = $(document.createElementNS(svgNS, 'text'))
            .attr({ x: nodeWidth / 2, y: nodeHeight / 2 - 10, 'text-anchor': 'middle', 'dominant-baseline': 'central', 'font-size': '0.9em', fill: textColor, 'font-weight': 'bold' })
            .text(node.nodeName || node.nodeId);
        const textNodeId = $(document.createElementNS(svgNS, 'text'))
            .attr({ x: nodeWidth / 2, y: nodeHeight / 2 + 5, 'text-anchor': 'middle', 'dominant-baseline': 'central', 'font-size': '0.7em', fill: textColor })
            .text(`(ID: ${node.nodeId})`);
        const textStatus = $(document.createElementNS(svgNS, 'text'))
            .attr({ x: nodeWidth / 2, y: nodeHeight / 2 + 20, 'text-anchor': 'middle', 'dominant-baseline': 'central', 'font-size': '0.75em', fill: textColor })
            .text(i18n.translate('logsPage.logStatuses.' + (nodeStatusInfo.status || 'unknown').toLowerCase(), nodeStatusInfo.status || 'Unknown'));
        g.append(textNodeName).append(textNodeId).append(textStatus);

        const nodeCenterX = nodeHMargin + nodeWidth / 2;
        const nodeCenterY = currentY + nodeHeight / 2;
        g.attr('transform', `translate(${nodeHMargin}, ${currentY})`);
        g.data('nodeStatusInfo', nodeStatusInfo); // Store all status info for click
        nodePositions[node.nodeId] = { x: nodeCenterX, y: nodeCenterY };

        g.on('click', function() {
           const info = $(this).data('nodeStatusInfo');
           $('#visNodeId').text(info.nodeId);
           $('#visNodeStatus').text(i18n.translate('logsPage.logStatuses.' + (info.status || 'unknown').toLowerCase(), info.status || 'Unknown'));
           $('#visNodeLastLogId').text(info.lastLogId || 'N/A');
           $('#visNodeLastStartTime').text(info.lastStartTime ? new Date(info.lastStartTime).toLocaleString() : 'N/A');
           $('#visNodeLastEndTime').text(info.lastEndTime ? new Date(info.lastEndTime).toLocaleString() : 'N/A');
           $('#visNodeLastMessage').text(info.lastMessage || 'N/A');
           $('#visSelectedNodeDetails').show();
           // Potentially apply i18n to the static labels in visSelectedNodeDetails if not already covered
           // by a general applyTranslations call after modal content is set.
        });
        svg.append(g);
        currentY += nodeHeight + nodeVMargin;
    });

    (edges || []).forEach(function(edge) {
        const sourcePos = nodePositions[edge.fromNodeId];
        const targetPos = nodePositions[edge.toNodeId];
        if (sourcePos && targetPos) {
            const line = $(document.createElementNS(svgNS, 'line'))
                .attr({ x1: sourcePos.x, y1: sourcePos.y, x2: targetPos.x, y2: targetPos.y, stroke: '#6c757d', 'stroke-width': 1.5, 'marker-end': `url(#${arrowheadId})` });
            svg.append(line);
        }
    });
    svg.attr('height', Math.max(300, currentY)); // Ensure min height for container
    svgContainer.append(svg);
}
