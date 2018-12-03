$(document).ready(function () {


    /*
     * Use promises for the different alias level requests
     */
    var promisesAliasResolve = [];

    /*
     * Start to get alias (if exists) from the current document (it will be the
     * root in alias tree)
     */
    let aliasCurrentDocument = '';


    if (!isEmpty($("#mir-aliaspart").val())) {

        aliasCurrentDocument = $("#mir-aliaspart").val();


        let aliasTree = new AliasTree();
        aliasTree.add(aliasCurrentDocument, 'root');

        console.log('alias-modal.js: look into related items dependency to get the full url!')

        getAliasContext(getRelatedItemIds(), aliasCurrentDocument, aliasTree);

        function simpletest() {
            console.log(aliasTree);
        }

        setTimeout(simpletest, 5000);
    }

    // observe related item
    $('.mir-related-item-search .form-inline').observe('added', 'span', function(changedItem) {

        console.log('alias-modal.js: There was added a new related item. Refresh the alias tree.');

        /*
         * Get empty alias tree
         */
        let aliasTree = new AliasTree();

        /*
         * get current field value from alias
         */
        aliasCurrentDocument = $("#mir-aliaspart").val();
        aliasTree.add(aliasCurrentDocument, 'root');

        getAliasContext(getRelatedItemIds(), aliasCurrentDocument, aliasTree);

        function simpletest() {
            console.log(aliasTree);
        }

        setTimeout(simpletest, 5000);

    });


    function requestMCRObjectMetadata(mycoreId) {

        return $.ajax({
            url: webApplicationBaseURL + "api/v2/objects/" + mycoreId,
            dataType: "xml",
            type: "GET"
        });
    }

    function getRelatedItemIds() {

        let relatedItemIds = [];

        $.each($('.mir-related-item-search .form-inline span'), (index, element) => {

            if (!element.textContent || 0 === element.textContent.length) {

                console.log('alias-modal.js: There have been added a new related item without an id to the frontend.')
            } else {
                relatedItemIds.push(element.textContent);
            }
        });

        console.log('alias-modal.js: related Item mycore Ids are ' + relatedItemIds);

        return relatedItemIds;

    }

    function getAliasContext(mycoreIds, parent, aliasTree) {

        $.each(mycoreIds, (index, mycoreId) => {

            requestMCRObjectMetadata(mycoreId).then((data) => {

                var alias = $(data).find('servflag[type="alias"]').text();
                var relatedItems = [];

                $(data).find('mods\\:mods > mods\\:relatedItem').each(function () {
                    relatedItems.push($(this).attr('xlink:href'));
                });

                console.log('alias-modal.js: Build alias Context for mycore id: ' + mycoreId);
                console.log('alias-modal.js: {mycoreId : ' + mycoreId + ', alias : ' + alias + ', relatedItems : {' + relatedItems + '}');

                aliasTree.add(alias, parent);

                getAliasContext(relatedItems, alias, aliasTree);
            });
        });
    }

    // javascript helper methods
    function isEmpty(value) {
        return typeof value == 'string' && !value.trim() || typeof value == 'undefined' || value === null;
    }

});

class AliasTree {
    constructor() {
        this._root = null;
    }

    _traverse(callback) {
        function walk(node) {
            callback(node);
            node.children.forEach(walk);
        }

        walk(this._root);
    }

    add(value, parentValue) {
        var newNode = {
            value,
            children: []
        };

        if (null === this._root) {
            this._root = newNode;
            return;
        }

        this._traverse(function (node) {
            if (parentValue === node.value) {
                node.children.push(newNode);
            }
        });
    }

    remove(value) {
        this._traverse(function (node) {
            node.children.some(function (childNode, index) {
                if (value === childNode.value) {
                    return !!node.children.splice(index, 1);
                }
            });
        });
    }

    search(value) {
        let exists = false;

        this._traverse(function (node) {
            if (value === node.value) {
                exists = true;
            }
        });

        return exists;
    }
}