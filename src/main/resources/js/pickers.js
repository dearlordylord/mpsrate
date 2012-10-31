AJS.$.namespace("GB.gadget.fields");
GB.gadget.fields.picker = function(gadget, userpref, options, label) {
    if(!AJS.$.isArray(options)){
        options = [options];
    }

      return {
        id: "picker_" + userpref,
        userpref: userpref,
        label: label||"Picker",
        description: "Select",
        type: "multiselect",
        selected: gadget.getPref(userpref),
        options: options
      };
};
GB.gadget.fields.datePicker = function(gadget, label, userPref)
{
    return {
                userpref: userPref,
                id: "dateField-picker-" + userPref,
                label: label,
                description: "Choose a date.",
                type: "callbackBuilder",
                callback: function(parentDiv){
                    parentDiv.append(
                        jQuery("<input/>").attr({ id: "dateField-" + userPref, type: "text", name: userPref }).val(gadget.getPref(userPref)).datepicker({ showAnim: '' })
                    );
                }
           };
};