$(function() {
  var $wfdProfiles = $('.wfd-profiles');
  
  function prepareForm($modal, $target) {
    var $form = $modal.find('form');
    $form.find('#form3-firstName').focus()
    $form.submit(function() {
      $.ajax({
        url: $form.attr('action'),
        data: $form.serialize(),
        type: 'post',
        success: function(json) {
          $target.text(json.fullName);
          $.modal.close();
        },
        error: function(res) {
          alert(res.responseText);
        }
      });
      return false;
    });
  }

  $wfdProfiles.find('a').on('click', function() {
    let $target = $(this).parent().prev()
    $.ajax({
      url: $(this).attr('href'),
      success: function(html) {
        prepareForm($.modal(html, 'wfd-modal'), $target);
      },
      error: function(res) {
        alert(res.responseText);
      }
    })
    return false;
  })
});
