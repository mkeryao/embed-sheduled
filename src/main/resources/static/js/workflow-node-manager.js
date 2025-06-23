(function() {
    'use strict';

    const NodeManager = {
        nodes: [],
        edges: [],
        _nextNodeIdCounter: 1,
        _nextEdgeIdCounter: 1,

        _generateNodeId: function(type) {
            return `${type || 'node'}-${this._nextNodeIdCounter++}`;
        },
        _generateEdgeId: function() {
            return `edge-${this._nextEdgeIdCounter++}`;
        },

        addWorkflowNode: function(nodeType = 'process') {
            if (!window.SVGManager) {
                console.error("SVGManager not found. Cannot add node.");
                return null;
            }
            // Ensure SVG is initialized by SVGManager if not already
            let currentDagSvg = typeof window.getDagSvg === 'function' ? window.getDagSvg() : null;
            if (!currentDagSvg) {
                 if (typeof window.SVGManager.initializeSvg === 'function') {
                    currentDagSvg = window.SVGManager.initializeSvg('dagContainer'); // Default container ID
                 }
                 if (!currentDagSvg) {
                     console.error("Failed to initialize SVG via SVGManager. Cannot add node.");
                     return null;
                 }
            }
            
            const newNode = {
                id: this._generateNodeId(nodeType),
                nodeId: null, // For compatibility if old data used this
                nodeName: `Node ${this._nextNodeIdCounter - 1} (${nodeType})`, // Using nodeName instead of name
                type: nodeType,
                x: window.SVGManager.nextNodeX,
                y: window.SVGManager.nextNodeY,
                taskConfigId: null, 
                parameters: {} 
            };
            newNode.nodeId = newNode.id; 

            window.SVGManager.nextNodeX += 50; // Adjust next position
            if (currentDagSvg && currentDagSvg.viewBox && currentDagSvg.viewBox.baseVal &&
                window.SVGManager.nextNodeX > (currentDagSvg.viewBox.baseVal.width - window.SVGManager.nodeWidth - 20)) {
                window.SVGManager.nextNodeX = 50;
                window.SVGManager.nextNodeY += (window.SVGManager.nodeHeight + 30);
            }
            
            this.nodes.push(newNode);
            this.renderDAG();
            this.serializeWorkflow(); 
            return newNode;
        },

        deleteNode: function(nodeIdToDelete) {
            let actualNodeId = nodeIdToDelete;
            // Find index, also handling if a raw index number was mistakenly passed
            const nodeIndex = this.nodes.findIndex(function(n){ return n.id === nodeIdToDelete; });
            
            if (nodeIndex === -1) { // If ID not found directly
                // Check if nodeIdToDelete is a numeric string or number (representing an old index-based call)
                const numNodeId = parseInt(nodeIdToDelete);
                if (!isNaN(numNodeId) && numNodeId >= 0 && numNodeId < this.nodes.length) {
                    // This case is less likely with string IDs but kept for robustness if old code called it
                    // actualNodeId = this.nodes[numNodeId].id; 
                    console.warn(`Attempted to delete node by numerical index ${nodeIdToDelete}, which is ambiguous. Please use string ID.`);
                    return; // Avoid deleting by raw index if IDs are strings
                } else {
                    console.warn(`Node with ID ${nodeIdToDelete} not found for deletion.`);
                    return;
                }
            } else {
                 actualNodeId = this.nodes[nodeIndex].id;
            }
            
            this.nodes = this.nodes.filter(function(node){ return node.id !== actualNodeId; });
            this.edges = this.edges.filter(function(edge){ return edge.fromNodeId !== actualNodeId && edge.toNodeId !== actualNodeId; });
            
            this.renderDAG();
            this.serializeWorkflow(); 
        },
        
        getNodeById: function(id) {
            return this.nodes.find(function(n){ return n.id === id; });
        },

        updateNode: function(nodeId, updatedProps) {
            const node = this.getNodeById(nodeId);
            if (node) {
                Object.assign(node, updatedProps);
                // Visual update for name might be handled by renderDAG or specifically
                const currentDagSvg = typeof window.getDagSvg === 'function' ? window.getDagSvg() : null;
                if (currentDagSvg) {
                    const nodeElement = currentDagSvg.querySelector(`.workflow-node[data-node-id="${nodeId}"]`);
                    if (nodeElement) {
                        const textLabel = nodeElement.querySelector('.workflow-node-label');
                        if (textLabel) textLabel.textContent = node.nodeName; // Direct update for responsiveness
                    }
                }
                // this.renderDAG(); // Could be called, or rely on serialize and future full redraws
                this.serializeWorkflow(); 
            } else {
                console.warn(`Node ${nodeId} not found for update.`);
            }
        },

        addEdge: function(fromNodeId, toNodeId, expression = "", priority = 1) {
            if (!this.getNodeById(fromNodeId) || !this.getNodeById(toNodeId)) {
                console.error("Cannot add edge: one or both nodes not found.", fromNodeId, toNodeId);
                return null;
            }
            if (fromNodeId === toNodeId) {
                 console.warn("Cannot add edge from a node to itself.");
                 return null;
            }
            // Check if edge already exists
            if (this.edges.some(function(e){ return e.fromNodeId === fromNodeId && e.toNodeId === toNodeId; })) {
                console.warn(`Edge from ${fromNodeId} to ${toNodeId} already exists.`);
                return this.edges.find(function(e){ return e.fromNodeId === fromNodeId && e.toNodeId === toNodeId; });
            }

            const newEdge = {
                id: this._generateEdgeId(),
                fromNodeId: fromNodeId,
                toNodeId: toNodeId,
                expression: expression,
                priority: parseInt(priority) || 1
            };
            this.edges.push(newEdge);
            this.renderDAG();
            this.serializeWorkflow();
            return newEdge;
        },

        deleteEdge: function(edgeId) {
             this.edges = this.edges.filter(function(edge){ return edge.id !== edgeId; });
             this.renderDAG();
             this.serializeWorkflow();
        },

        updateEdge: function(edgeId, updatedProps) {
            const edge = this.edges.find(function(e){ return e.id === edgeId; });
            if (edge) {
                Object.assign(edge, updatedProps);
                this.renderDAG(); 
                this.serializeWorkflow();
            } else {
                console.warn(`Edge ${edgeId} not found for update.`);
            }
        },
        
        updateEdgesForNode: function(nodeId) {
            const currentDagSvg = typeof window.getDagSvg === 'function' ? window.getDagSvg() : null;
            if (!currentDagSvg || !window.SVGManager) return;

            const self = this; // For use in forEach
            const edgeElements = currentDagSvg.querySelectorAll(`.workflow-edge[data-from-node="${nodeId}"], .workflow-edge[data-to-node="${nodeId}"]`);
            edgeElements.forEach(function(edgeElement){
                const fromId = edgeElement.getAttribute('data-from-node');
                const toId = edgeElement.getAttribute('data-to-node');
                const sourceNode = self.getNodeById(fromId);
                const targetNode = self.getNodeById(toId);
                if (sourceNode && targetNode) {
                    const pathData = window.SVGManager.getEdgePathData(sourceNode, targetNode);
                    edgeElement.setAttribute('d', pathData);
                }
            });
        },

        renderDAG: function() {
            const currentDagSvg = typeof window.getDagSvg === 'function' ? window.getDagSvg() : null;
            if (!currentDagSvg || !window.SVGManager) {
                // Try to initialize SVG if it's not there yet, e.g. on first load
                if (window.SVGManager && typeof window.SVGManager.initializeSvg === 'function') {
                    if (!window.SVGManager.initializeSvg('dagContainer')) { // Default container ID
                        console.error('SVG not initialized and failed to initialize, cannot render DAG.');
                        return;
                    }
                } else {
                    console.error('SVGManager not available, cannot render DAG.');
                    return;
                }
            }
            
            // Re-fetch dagSvg after potential initialization
            const dagToRenderOn = typeof window.getDagSvg === 'function' ? window.getDagSvg() : null;
            if (!dagToRenderOn) {
                console.error('Failed to get SVG element for rendering.');
                return;
            }

            // Clear existing nodes and edges (but keep defs)
            const childNodes = Array.from(dagToRenderOn.childNodes);
            for (const child of childNodes) {
                if (child.tagName !== 'defs') { 
                    dagToRenderOn.removeChild(child);
                }
            }
            
            // Ensure defs and arrowhead marker exist (SVGManager might also do this, but good to be robust)
            let defs = dagToRenderOn.querySelector('defs');
            if (!defs) {
                defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs'); // svgNS might not be in scope here
                dagToRenderOn.insertBefore(defs, dagToRenderOn.firstChild); 
            }
            if (!defs.querySelector('#arrowhead')) {
                const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
                marker.setAttribute('id', 'arrowhead');
                marker.setAttribute('viewBox', '0 0 10 10'); marker.setAttribute('refX', '9'); 
                marker.setAttribute('refY', '5'); marker.setAttribute('markerWidth', '6');
                marker.setAttribute('markerHeight', '6'); marker.setAttribute('orient', 'auto-start-reverse');
                const markerPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                markerPath.setAttribute('d', 'M 0 0 L 10 5 L 0 10 z'); markerPath.setAttribute('fill', '#555');
                marker.appendChild(markerPath); defs.appendChild(marker);
            }

            // Render nodes
            this.nodes.forEach(function(node) {
                if (window.SVGManager && typeof window.SVGManager.createNodeElement === 'function') {
                    const nodeElement = window.SVGManager.createNodeElement(node);
                    dagToRenderOn.appendChild(nodeElement);
                }
            });
            
            // Render edges
            const self = this;
            this.edges.forEach(function(edge) {
                const sourceNode = self.getNodeById(edge.fromNodeId);
                const targetNode = self.getNodeById(edge.toNodeId);
                if (sourceNode && targetNode) {
                    if (window.SVGManager && typeof window.SVGManager.createEdgeElement === 'function') {
                        const edgeElement = window.SVGManager.createEdgeElement(edge, sourceNode, targetNode);
                        dagToRenderOn.appendChild(edgeElement);
                    }
                }
            });
        },

        loadWorkflow: function(nodesJsonString, edgesJsonString) {
            try {
                this.nodes = typeof nodesJsonString === 'string' ? JSON.parse(nodesJsonString || '[]') : (nodesJsonString || []);
                this.edges = typeof edgesJsonString === 'string' ? JSON.parse(edgesJsonString || '[]') : (edgesJsonString || []);
                
                let maxNodeIdNum = 0;
                this.nodes.forEach(function(n) {
                    if (n.id && typeof n.id === 'string' && n.id.includes('-')) {
                       const numPart = parseInt(n.id.substring(n.id.lastIndexOf('-') + 1));
                       if (!isNaN(numPart) && numPart > maxNodeIdNum) maxNodeIdNum = numPart; 
                    } else if (n.id && !isNaN(parseInt(n.id)) && parseInt(n.id) > maxNodeIdNum) { 
                        maxNodeIdNum = parseInt(n.id);
                    }
                });
                this._nextNodeIdCounter = maxNodeIdNum + 1;

                let maxEdgeIdNum = 0;
                this.edges.forEach(function(e) {
                     if (e.id && typeof e.id === 'string' && e.id.includes('-')) {
                       const numPart = parseInt(e.id.substring(e.id.lastIndexOf('-') + 1));
                       if (!isNaN(numPart) && numPart > maxEdgeIdNum) maxEdgeIdNum = numPart;
                    } else if (e.id && !isNaN(parseInt(e.id)) && parseInt(e.id) > maxEdgeIdNum) {
                        maxEdgeIdNum = parseInt(e.id);
                    }
                });
                this._nextEdgeIdCounter = maxEdgeIdNum + 1;

                if (window.SVGManager) {
                    if (this.nodes.length === 0) {
                        window.SVGManager.nextNodeX = 50; 
                        window.SVGManager.nextNodeY = 50;
                    } else {
                        // Find max Y to place new nodes below existing ones
                        const maxYNode = this.nodes.reduce(function(prev, current) {
                            return ((prev.y + window.SVGManager.nodeHeight) > (current.y + window.SVGManager.nodeHeight)) ? prev : current;
                        }, this.nodes[0] || {y:0}); // handle empty nodes array initial case
                        window.SVGManager.nextNodeX = 50;
                        window.SVGManager.nextNodeY = (maxYNode.y || 0) + window.SVGManager.nodeHeight + 30;
                    }
                }
                this.renderDAG();
            } catch (e) {
                console.error('Error parsing workflow JSON:', e);
                this.nodes = []; this.edges = [];
                this.renderDAG(); // Attempt to render an empty state
            }
        },

        serializeWorkflow: function() {
            const nodesTextarea = document.getElementById('workflowNodesJson');
            const edgesTextarea = document.getElementById('workflowEdgesJson');

            if (nodesTextarea) {
                try {
                    nodesTextarea.value = JSON.stringify(this.nodes, null, 2);
                } catch (e) { console.error("Error stringifying nodes:", e); }
            }
            if (edgesTextarea) {
                 try {
                    edgesTextarea.value = JSON.stringify(this.edges, null, 2);
                } catch (e) { console.error("Error stringifying edges:", e); }
            }
        },

        initializeEditor: function(containerId, nodesJson, edgesJson) {
            if (window.SVGManager && typeof window.SVGManager.initializeSvg === 'function') {
                window.SVGManager.initializeSvg(containerId);
                this.loadWorkflow(nodesJson, edgesJson); 
            } else {
                console.error("SVGManager not available for editor initialization.");
            }
        }
    };

    window.NodeManager = NodeManager;

})();
