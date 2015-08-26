<%@ page contentType="text/html;charset=UTF-8" language="java" pageEncoding="UTF-8"
        %><%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"
        %><%@ taglib prefix="stripes" uri="http://stripes.sourceforge.net/stripes.tld"
        %><jsp:useBean id="actionBean" scope="request" type="com.manydesigns.portofino.pageactions.custom.CustomAction"
        /><stripes:layout-render name="/theme/templates/${actionBean.pageTemplate}/normal.jsp">
    <stripes:layout-component name="pageTitle">
        <c:out value="${actionBean.page.title}"/>
    </stripes:layout-component>
    <stripes:layout-component name="pageBody">
       <!-- <link rel="stylesheet" href="//netdna.bootstrapcdn.com/bootstrap/3.1.0/css/bootstrap.min.css">
        <script src="//netdna.bootstrapcdn.com/bootstrap/3.1.0/js/bootstrap.min.js" type="text/javascript"></script>
        -->
        <link href="<stripes:url value="/theme/fancytree/skin-bootstrap/ui.fancytree.css"/>" rel="stylesheet" type="text/css"
              class="skinswitcher">

        <script src="<stripes:url value="/theme/fancytree/src/jquery.fancytree.js"/>"       type="text/javascript"></script>
        <script src="<stripes:url value="/theme/fancytree/src/jquery.fancytree.dnd.js"/>"   type="text/javascript"></script>
        <script src="<stripes:url value="/theme/fancytree/src/jquery.fancytree.edit.js"/>"  type="text/javascript"></script>
        <script src="<stripes:url value="/theme/fancytree/src/jquery.fancytree.glyph.js"/>" type="text/javascript"></script>
        <script src="<stripes:url value="/theme/fancytree/src/jquery.fancytree.table.js"/>" type="text/javascript"></script>
        <script src="<stripes:url value="/theme/fancytree/src/jquery.fancytree.wide.js"/>"  type="text/javascript"></script>

        <!-- (Irrelevant source removed.) -->

        <style type="text/css">
                /* Define custom width and alignment of table columns */
            #treetable {
                table-layout: fixed;
            }
            #treetable tr td:nth-of-type(1) {
                text-align: right;
            }
            #treetable tr td:nth-of-type(2) {
                text-align: center;
            }
            #treetable tr td:nth-of-type(3) {
                min-width: 100px;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
        </style>


        <!-- Add code to initialize the tree when the document is loaded: -->
        <script type="text/javascript">
            glyph_opts = {
                map: {
                    doc: "glyphicon glyphicon-file",
                    docOpen: "glyphicon glyphicon-file",
                    checkbox: "glyphicon glyphicon-unchecked",
                    checkboxSelected: "glyphicon glyphicon-check",
                    checkboxUnknown: "glyphicon glyphicon-share",
                    dragHelper: "glyphicon glyphicon-play",
                    dropMarker: "glyphicon glyphicon-arrow-right",
                    error: "glyphicon glyphicon-warning-sign",
                    expanderClosed: "glyphicon glyphicon-plus-sign",
                    expanderLazy: "glyphicon glyphicon-plus-sign",  // glyphicon-expand
                    expanderOpen: "glyphicon glyphicon-minus-sign",  // glyphicon-collapse-down
                    folder: "glyphicon glyphicon-folder-close",
                    folderOpen: "glyphicon glyphicon-folder-open",
                    loading: "glyphicon glyphicon-refresh"
                }
            };
            $(function(){
                // Initialize Fancytree
                $("#tree").fancytree({
                    extensions: ["dnd", "edit", "glyph", "wide"],
                    checkbox: true,
                    dnd: {
                        dragStart: function(node, data) { return true; },
                        dragEnter: function(node, data) { return false; },
                        dragDrop: function(node, data) { data.otherNode.copyTo(node, data.hitMode); }
                    },
                    glyph: glyph_opts,
                    selectMode: 2,
                    source: {url: "?ajaxSampleData", debugDelay: 1000},
                    toggleEffect: { effect: "drop", options: {direction: "left"}, duration: 400 },
                    wide: {
                        iconWidth: "1em",     // Adjust this if @fancy-icon-width != "16px"
                        iconSpacing: "0.5em", // Adjust this if @fancy-icon-spacing != "3px"
                        levelOfs: "1.5em"     // Adjust this if ul padding != "16px"
                    },

                    iconClass: function(event, data){
                        // if( data.node.isFolder() ) {
                        //   return "glyphicon glyphicon-book";
                        // }
                    },
                    lazyLoad: function(event, data) {
                        data.result = {url: "ajax-sub2.json", debugDelay: 1000};
                    }
                });

                $("#treetable").fancytree({
                    extensions: ["dnd", "edit", "glyph", "table"],
                    checkbox: true,
                    dnd: {
                        dragStart: function(node, data) { return true; },
                        dragEnter: function(node, data) { return true; },
                        dragDrop: function(node, data) { data.otherNode.copyTo(node, data.hitMode); }
                    },
                    glyph: glyph_opts,
                    source: {url: "?ajaxSampleData", debugDelay: 1000},
                    table: {
                        checkboxColumnIdx: 1,
                        nodeColumnIdx: 2
                    },

                    activate: function(event, data) {
                    },
                    lazyLoad: function(event, data) {
                        data.result = {url: "ajax-sub2.json", debugDelay: 1000};
                    },
                    renderColumns: function(event, data) {
                        var node = data.node,
                                $tdList = $(node.tr).find(">td");
                        $tdList.eq(0).text(node.getIndexHier());
                        $tdList.eq(3).text(!!node.folder);
                    }
                });
            });
        </script>


        <i>This is an example page in which we use <a href="https://github.com/mar10/fancytree">fancytree</a>
            to show how it is possible to create json data in Portofino and use them in an Ajax call</i>

        <div class="panel panel-default">
            <div class="panel-heading">
                <b>Taxonomy</b>
            </div>
            <div id="tree" class="panel-body fancytree-colorize-hover">
            </div>
            <div class="panel-footer">

            </div>
        </div>


    </stripes:layout-component>
</stripes:layout-render>