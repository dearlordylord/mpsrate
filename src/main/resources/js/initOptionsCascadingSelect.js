(function () {

    function initCascadingSelect(el) {
        AJS.$(el || document.body).find('div.aui-field-cascadingselect').add('tr.aui-field-cascadingselect').add('td.aui-field-cascadingselect').each(function () {
            var $container = AJS.$(this),
                parent = $container.find('.cascadingselect-radio-parent'),
                oldClass = "",
                child = $container.find('.cascadingselect-radio-child'),
                childOptions = child.find('option'),
                selectedChild = child.find(':selected');
                console.warn(child);
                console.warn(childOptions);
            function update() {
                var placeholder,
                    currentClass = parent.find('input:checked').attr('class');
                // Compare so we're not redrawing the child dropdown when changing between the options with the class "default-option"
                if (currentClass !== oldClass) {
                    // Hide all the options other than ones relating to the selected parent
                    placeholder = jQuery("<span />").insertAfter(child);
                    child.detach();
                    child.find('option').each(function (i, elem) {
                        elem.parentNode.removeChild(this);
                    });
                    child.insertAfter(placeholder);
                    placeholder.remove();
                    childOptions.filter('.'+parent.find('input:checked').attr('class')).appendTo(child);
                    // Select the option which is to be selected on page load - default to the first one if none specified.
                    if (selectedChild.hasClass(parent.find('input:checked').attr('class'))) {
                        child.val(selectedChild.val());
                    } else {
                        child.val(child.find('option:first').val());
                    }
                    oldClass = currentClass;
                }
            }
            parent.bind('cascadingSelectChanged', update)
                  .change(function(){
                        AJS.$(this).trigger('cascadingSelectChanged');
                  })
                  .trigger('cascadingSelectChanged');
        });
    }

    JIRA.bind(JIRA.Events.NEW_CONTENT_ADDED, function (e, context, reason) {
        if (reason !== JIRA.CONTENT_ADDED_REASON.panelRefreshed) {
            initCascadingSelect(context);
        }
    });

})();