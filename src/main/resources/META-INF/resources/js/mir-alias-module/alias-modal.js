$(document).ready(function () {


    /*
     * alias configuration parameter (to do search for a way to resolve this directly from mycore.properties
     */
    var aliasConfParameter = 'go/';
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


        let aliasPaths = [];


        console.log('alias-modal.js: look into related items dependency to get the full url!')

        $.each(getRelatedItemIds(), (index, mycoreId) => {
            getAliasContext(mycoreId, aliasCurrentDocument, aliasPaths);
        });

        function simpletest() {
            console.log(aliasPaths);

            $.each(aliasPaths, (index, path) => {

                index = index + 1;

                let urlHtmlTemplate = `
                <div class="form-group">
                  <label class="col-md-3 control-label">
                    URL-` + index + `-:
                  </label>
                  <div class="col-md-6 ">
                    <input name="" value="` + webApplicationBaseURL + aliasConfParameter + path + `" class="form-control" type="text">
                  </div>
                </div>
            `;
                $('div[class="mir-fieldset-content alias-fieldset"]').append(urlHtmlTemplate);
            });
        }

        setTimeout(simpletest, 5000);
    }

    // observe related item
    $('.mir-related-item-search .form-inline').observe('added', 'span', function (changedItem) {

        console.log('alias-modal.js: There was added a new related item. Refresh the alias tree.');


        let aliasPaths = [];

        /*
         * get current field value from alias
         */
        aliasCurrentDocument = $("#mir-aliaspart").val();

        $.each(getRelatedItemIds(), (index, mycoreId) => {
            getAliasContext(mycoreId, aliasCurrentDocument, aliasPaths);
        });


        function simpletest() {
            console.log(aliasPaths);
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

    function getAliasContext(mycoreId, path, aliasPaths) {

        requestMCRObjectMetadata(mycoreId).then((data) => {

            var alias = $(data).find('servflag[type="alias"]').text();
            var relatedItems = [];

            $(data).find('mods\\:mods > mods\\:relatedItem').each(function () {
                relatedItems.push($(this).attr('xlink:href'));
            });

            console.log('alias-modal.js: Build alias Context for mycore id: ' + mycoreId);
            console.log('alias-modal.js: {mycoreId : ' + mycoreId + ', alias : ' + alias + ', relatedItems : {' + relatedItems + '}');

            path = alias + '/' + path;

            if (relatedItems && relatedItems.length) {

                $.each(relatedItems, (index, mycoreId) => {
                    getAliasContext(mycoreId, path, aliasPaths);
                });
            } else {
                aliasPaths.push(path);
            }
        });
    }

    // javascript helper methods
    function isEmpty(value) {
        return typeof value == 'string' && !value.trim() || typeof value == 'undefined' || value === null;
    }
});