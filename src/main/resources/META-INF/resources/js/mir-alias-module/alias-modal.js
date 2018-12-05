$(document).ready(function () {
    /*
     * alias configuration parameter (to do search for a way to resolve this
     * directly from mycore.properties
     */
    var aliasConfParameter = 'go/';
    /*
     * Start to get alias (if exists) from the current document (it will be the
     * root in alias tree)
     */
    let aliasCurrentDocument = $("#mir-aliaspart").val();
    let aliasPaths = [];

    console.log('alias-modal.js: look into related items dependency to get full alias urls!')

    $.when.apply($, getAliasResolvePromises([])).then(() => {

        if (aliasPaths.length > 0) {

            console.log('alias-modal.js: All possible paths have been created!');
            appendGeneratedUrls(aliasPaths);

        } else {
            console.log('alias-modal.js: There are no alias paths! Create default URL');
            appendDefaultUrl();
        }
    });


    // observe related item
    $('.mir-related-item-search .form-inline').observe('added', 'span', function (changedItem) {

        console.log('alias-modal.js: There was added a new related item. Refresh the alias tree.');

        aliasPaths = [];

        /*
         * get current field value from alias
         */
        aliasCurrentDocument = $("#mir-aliaspart").val();

        /*
         * remove obsolete generated url fields
         */
        $(".generatedAliasUrl").remove();

        $.when.apply($, getAliasResolvePromises([])).then(() => {

            if (aliasPaths.length > 0) {
                console.log('alias-modal.js: All possible paths have been updated!');
                appendGeneratedUrls(aliasPaths);
            } else {
                console.log('alias-modal.js: There are no alias paths after update! Create default URL');
                appendDefaultUrl();
            }
        });
    });

    /*
     * observe modification on alias part to update generated url fields
     */
    $("#mir-aliaspart").on("focusin", function () {

        let previousValue = $("#mir-aliaspart").val();
        $(this).data('previousValue', previousValue);

    }).on("change paste keyup", function () {

        let prevAliaspart = $(this).data('previousValue');
        let currentAliaspart = $("#mir-aliaspart").val();

        $('.generatedAliasUrlInput').map(function () {

            let generatedUrl = $(this).attr('value');

            if (generatedUrl.endsWith(prevAliaspart)) {

                $(this).attr('value',
                    generatedUrl.substring(0, generatedUrl.length - prevAliaspart.length) + currentAliaspart);
            }
        });

        $(this).data('previousValue', currentAliaspart);
    });

    function appendDefaultUrl() {

        let defaultUrl = `
                <div class="form-group generatedAliasUrl">
                  <label class="col-md-3 control-label">
                    URL:
                  </label>
                  <div class="col-md-6 ">
                    <input name="" value="` + webApplicationBaseURL + aliasConfParameter + $("#mir-aliaspart").val() + `" class="form-control generatedAliasUrlInput" type="text">
                  </div>
                </div>
            `;
        $('div[class="mir-fieldset-content alias-fieldset"]').append(defaultUrl);
    }

    function appendGeneratedUrls(aliasPaths) {
        $.each(aliasPaths, (index, path) => {

            index = index + 1;

            let urlHtmlTemplate = `
                <div class="form-group generatedAliasUrl">
                  <label class="col-md-3 control-label">
                    URL-` + index + `-:
                  </label>
                  <div class="col-md-6 ">
                    <input name="" value="` + webApplicationBaseURL + aliasConfParameter + path + `" class="form-control generatedAliasUrlInput" type="text">
                  </div>
                </div>
            `;
            $('div[class="mir-fieldset-content alias-fieldset"]').append(urlHtmlTemplate);
        });
    }

    function getAliasResolvePromises(promisesAliasResolve) {

        $.each(getRelatedItemIds(), (index, mycoreId) => {
            promisesAliasResolve.push(getAliasContext(mycoreId, aliasCurrentDocument, aliasPaths));
        });

        return promisesAliasResolve;
    }

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

        console.log('alias-modal.js: Build alias Context for related mycore item with id: ' + mycoreId);

        return $.Deferred((dfd) => {
            resolveAliasChain(mycoreId, path, aliasPaths, dfd);
        }).promise();
    }

    function resolveAliasChain(mycoreId, path, aliasPaths, dfd) {

        requestMCRObjectMetadata(mycoreId).then((data) => {

            var alias = $(data).find('servflag[type="alias"]').text();
            var relatedItems = [];

            $(data).find('mods\\:mods > mods\\:relatedItem').each(function () {
                relatedItems.push($(this).attr('xlink:href'));
            });

            path = alias + '/' + path;

            if (relatedItems && relatedItems.length) {

                $.each(relatedItems, (index, mycoreId) => {
                    resolveAliasChain(mycoreId, path, aliasPaths, dfd);
                });
            } else {
                console.log('alias-modal.js: Alias chain was build for path: ' + path);

                aliasPaths.push(path);
                dfd.resolve();
            }
        });
    }

    // javascript helper methods
    function isEmpty(value) {
        return typeof value == 'string' && !value.trim() || typeof value == 'undefined' || value === null;
    }
});