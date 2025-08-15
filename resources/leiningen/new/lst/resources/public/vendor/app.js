$(document).ready(function () {
  // Global flags
  var isFormSubmitting = false;
  var allowSubgridModalClose = false;

  // Init main DataTable
  var table = $('.dataTable').DataTable({
    stateSave: true,
    responsive: true,
    autoWidth: false,
    paging: true,
    pageLength: 10,
    lengthChange: true,
    lengthMenu: [[5, 10, 25, 50, 100], [5, 10, 25, 50, 100]],
    searching: true,
    info: true,
    ordering: true,
    dom: "<'row mb-3'<'col-sm-12 col-md-4'l><'col-sm-12 col-md-4'B><'col-sm-12 col-md-4'f>>" +
      "<'row'<'col-sm-12'tr>>" +
      "<'row mt-3'<'col-sm-12 col-md-5'i><'col-sm-12 col-md-7'p>>",
    buttons: [
      { extend: 'copy', className: 'btn btn-primary btn-sm' },
      { extend: 'csv', className: 'btn btn-primary btn-sm' },
      { extend: 'excel', className: 'btn btn-primary btn-sm' },
      { extend: 'pdf', className: 'btn btn-primary btn-sm' },
      { extend: 'print', className: 'btn btn-primary btn-sm' }
    ],
    language: {
      search: '_INPUT_',
      searchPlaceholder: 'Search...',
      lengthMenu: 'Show _MENU_ entries',
      info: 'Showing _START_ to _END_ of _TOTAL_ entries',
      paginate: {
        previous: '<i class="bi bi-chevron-left"></i>',
        next: '<i class="bi bi-chevron-right"></i>'
      }
    }
  });

  $('.dataTables_length select').addClass('form-select form-select-sm bg-light border-0 shadow-sm');

  // Go to last page after create
  if (localStorage.getItem('datatable_goto_last') === '1') {
    table.page('last').draw('page');
    localStorage.removeItem('datatable_goto_last');
  }

  // Open form modal via AJAX
  $(document).on('click', '.new-record-btn, .edit-record-btn', function (e) {
    e.preventDefault();
    var url = $(this).data('url');
    var modal = $('#exampleModal');
    modal.find('.modal-title').text($(this).hasClass('new-record-btn') ? 'New Record' : 'Edit Record');
    modal.find('.modal-body').html('<div class="text-center p-4"><div class="spinner-border text-primary" role="status"></div></div>');
    $.get(url, function (data) {
      modal.find('.modal-body').html(data);
    });
    modal.modal('show');
  });

  // Submit modal form with FormData if file present
  $(document).on('submit', '#exampleModal form', function (e) {
    e.preventDefault();
    var $form = this;

    if (!$form.checkValidity()) {
      $form.reportValidity();
      return false;
    }

    var isNew = !$($form).find('[name="id"]').val();
    if (isNew) localStorage.setItem('datatable_goto_last', '1');

    // Detect subgrid context
    var subgridModal = $('#subgridModal');
    var isSubgridModalOpen = subgridModal.length > 0 && subgridModal.hasClass('show') && subgridModal.is(':visible');
    var modalSubgridUrl = subgridModal.data('current-subgrid-url');
    var modalParentId = subgridModal.data('parent-id');
    var localSubgridUrl = localStorage.getItem('current-subgrid-url');
    var localParentId = localStorage.getItem('parent-id');
    var isSubgridModalVisible = subgridModal.length > 0 && subgridModal.hasClass('show');
    var subgridUrl = modalSubgridUrl || localSubgridUrl;
    var parentId = modalParentId || localParentId;
    var isSubgridContext = !!(subgridUrl && parentId && isSubgridModalVisible);
    if (!isSubgridContext && isSubgridModalOpen) {
      isSubgridContext = true;
      if (!subgridUrl) subgridUrl = localStorage.getItem('current-subgrid-url');
      if (!parentId) parentId = localStorage.getItem('parent-id');
    }
    if (isSubgridContext) isFormSubmitting = true;

    var hasFileInput = $($form).find('input[type="file"]').length > 0;
    var ajaxOptions = {
      url: $($form).attr('action'),
      type: 'POST',
      success: function () {
        if (isSubgridContext) {
          if (subgridUrl && parentId) {
            subgridModal.find('#subgrid-content').html("<div class='text-center p-4'><div class='spinner-border text-primary' role='status'></div><div class='mt-2'>Refreshing...</div></div>");
            $.get(subgridUrl, function (newData) {
              if (typeof newData === 'string' && newData.trim() === '') {
                subgridModal.find('#subgrid-content').html("<div class='alert alert-warning'>No data available for the subgrid. Please contact support.</div>");
              } else {
                subgridModal.find('#subgrid-content').html(newData);
                var subTable = subgridModal.find('table.dataTable');
                if (subTable.length > 0 && $.fn.DataTable.isDataTable(subTable[0])) {
                  subTable.DataTable().destroy();
                  subTable.removeClass('dataTable');
                }
                var tableToInit = subgridModal.find('table');
                if (tableToInit.length > 0) {
                  if (!tableToInit.hasClass('dataTable')) tableToInit.addClass('dataTable');
                  try {
                    setTimeout(function () {
                      tableToInit.DataTable({
                        responsive: true,
                        pageLength: 10,
                        destroy: true,
                        language: {
                          search: 'Search:',
                          lengthMenu: 'Show _MENU_ entries',
                          info: 'Showing _START_ to _END_ of _TOTAL_ entries',
                          emptyTable: 'No data available in table',
                          paginate: { first: 'First', last: 'Last', next: 'Next', previous: 'Previous' }
                        }
                      });
                    }, 100);
                  } catch (err) { /* no-op */ }
                }
              }
            }).fail(function () {
              subgridModal.find('#subgrid-content').html("<div class='alert alert-danger'>Error refreshing subgrid content. Please contact support.</div>");
            });
          } else {
            subgridModal.find('#subgrid-content').html("<div class='alert alert-danger'>Error: Missing subgrid attributes. Please contact support.</div>");
          }
          $('#exampleModal').modal('hide');
          if (!subgridModal.hasClass('show')) {
            subgridModal.addClass('show').css('display', 'block');
          }
        } else {
          location.reload();
        }
      },
      error: function () {
        alert('Error saving record. Please try again.');
      }
    };

    if (hasFileInput) {
      var fd = new FormData($form);
      ajaxOptions.data = fd;
      ajaxOptions.contentType = false;
      ajaxOptions.processData = false;
    } else {
      ajaxOptions.data = $($form).serialize();
    }

    $.ajax(ajaxOptions);
  });

  // Image preview for dynamically injected forms
  $(document).on('change', 'input[type="file"][name="file"]', function () {
    var input = this;
    if (input.files && input.files[0]) {
      var reader = new FileReader();
      reader.onload = function (ev) {
        var $img = $('#image1');
        if ($img.length) {
          // Append cache-busting param so the browser doesn't reuse old blob
          var url = ev.target.result;
          var bust = Date.now();
          $img.attr('src', url + (url.indexOf('?') === -1 ? '?v=' + bust : '&v=' + bust));
        }
      };
      reader.readAsDataURL(input.files[0]);
    }
  });

  // Click-to-zoom images in modal
  $(document).on('click', '#exampleModal img', function () {
    var $img = $(this);
    var origW = $img.attr('width');
    var origH = $img.attr('height');
    if ($img.width() < 500) {
      $img.animate({ width: 500, height: 500 }, 200).addClass('shadow-lg');
    } else {
      var targetW = origW ? parseInt(origW, 10) : 95;
      var targetH = origH ? parseInt(origH, 10) : 71;
      $img.animate({ width: targetW, height: targetH }, 200).removeClass('shadow-lg');
    }
  });

  // Subgrid open
  $(document).on('click', '[data-subgrid-url]', function (e) {
    e.preventDefault();
    var subgridUrl = $(this).data('subgrid-url');
    var parentId = $(this).data('parent-id');
    var subgridModal = $('#subgridModal');
    if (!subgridUrl) {
      subgridModal.find('#subgrid-content').html("<div class='alert alert-danger'>Error: Subgrid URL is missing. Please contact support.</div>");
      return;
    }

    subgridModal.data('current-subgrid-url', subgridUrl);
    localStorage.setItem('current-subgrid-url', subgridUrl);

    if (parentId) {
      subgridModal.data('parent-id', parentId);
      localStorage.setItem('parent-id', parentId);
    } else {
      parentId = localStorage.getItem('parent-id');
      if (parentId) {
        subgridModal.data('parent-id', parentId);
      } else {
        subgridModal.find('#subgrid-content').html("<div class='alert alert-danger'>Error: Parent ID is missing. Please contact support.</div>");
        return;
      }
    }

    subgridModal.find('#subgrid-content').html("<div class='text-center p-4'><div class='spinner-border text-primary' role='status'></div></div>");

    $.get(subgridUrl, function (data) {
      subgridModal.find('#subgrid-content').html(data);

      setTimeout(function () {
        if (!subgridModal.hasClass('show')) {
          subgridModal.addClass('show').css('display', 'block');
        }
      }, 100);

      var subTable = subgridModal.find('table.dataTable');
      if (subTable.length > 0 && !$.fn.DataTable.isDataTable(subTable[0])) {
        try {
          setTimeout(function () {
            subTable.DataTable({
              responsive: true,
              pageLength: 10,
              destroy: true,
              language: {
                search: 'Search:',
                lengthMenu: 'Show _MENU_ entries',
                info: 'Showing _START_ to _END_ of _TOTAL_ entries',
                emptyTable: 'No data available in table',
                paginate: { first: 'First', last: 'Last', next: 'Next', previous: 'Previous' }
              }
            });
          }, 100);
        } catch (error) { /* silent */ }
      }
    }).fail(function (xhr) {
      subgridModal.find('#subgrid-content').html("<div class='alert alert-danger'>Error loading subgrid content. Status: " + xhr.status + ". Please contact support.</div>");
    });

    subgridModal.modal('show');
  });

  // Modal guards for subgrid
  $(document).on('hide.bs.modal', '#subgridModal', function (e) {
    if (!allowSubgridModalClose && !e.relatedTarget && !isFormSubmitting) {
      e.preventDefault();
      e.stopPropagation();
      return false;
    }
  });

  $(document).on('hidden.bs.modal', '#subgridModal', function () {
    allowSubgridModalClose = false;
    try {
      var subgridModal = $(this);
      var subTable = subgridModal.find('table');
      if (subTable.length > 0) {
        subTable.each(function () {
          if ($.fn.DataTable.isDataTable(this)) {
            $(this).DataTable().destroy(true);
          }
        });
      }
    } catch (error) { /* silent */ }

    setTimeout(function () {
      if (!isFormSubmitting) {
        var subgridModal = $('#subgridModal');
        subgridModal.removeData('current-subgrid-url');
        subgridModal.removeData('parent-id');
        localStorage.removeItem('current-subgrid-url');
        localStorage.removeItem('parent-id');
      }
    }, 100);
  });

  // Reset flag when main form modal closes
  $(document).on('hidden.bs.modal', '#exampleModal', function () {
    setTimeout(function () { isFormSubmitting = false; }, 200);
  });

  // Allow manual closing of subgrid modal
  $(document).on('click', '#subgridModal .btn-close, #subgridModal [data-bs-dismiss="modal"]', function () {
    allowSubgridModalClose = true;
    $('#subgridModal').modal('hide');
  });

  // Confirm before delete; handle subgrid deletes via AJAX with refresh
  $(document).on('click', 'a[href*="/delete/"]', function (e) {
    var $a = $(this);
    // Respect disabled state
    if ($a.hasClass('disabled') || $a.attr('aria-disabled') === 'true') {
      e.preventDefault();
      return false;
    }

    var msg = $a.data('confirm') || 'Are you sure you want to delete this record?';
    if (!confirm(msg)) {
      e.preventDefault();
      return false;
    }

    var isInSubgrid = $a.closest('#subgridModal').length > 0;
    if (isInSubgrid) {
      e.preventDefault();
      var href = $a.attr('href');
      var subgridModal = $('#subgridModal');
      var subgridUrl = subgridModal.data('current-subgrid-url') || localStorage.getItem('current-subgrid-url');

      subgridModal.find('#subgrid-content').html("<div class='text-center p-4'><div class='spinner-border text-danger' role='status'></div><div class='mt-2'>Deleting...</div></div>");

      $.get(href, function () {
        if (subgridUrl) {
          subgridModal.find('#subgrid-content').html("<div class='text-center p-4'><div class='spinner-border text-primary' role='status'></div><div class='mt-2'>Refreshing...</div></div>");
          $.get(subgridUrl, function (newData) {
            if (typeof newData === 'string' && newData.trim() === '') {
              subgridModal.find('#subgrid-content').html("<div class='alert alert-warning'>No data available for the subgrid. Please contact support.</div>");
            } else {
              subgridModal.find('#subgrid-content').html(newData);
              // Re-init DataTable inside subgrid
              var subTable = subgridModal.find('table.dataTable');
              if (subTable.length > 0 && $.fn.DataTable.isDataTable(subTable[0])) {
                subTable.DataTable().destroy();
                subTable.removeClass('dataTable');
              }
              var tableToInit = subgridModal.find('table');
              if (tableToInit.length > 0) {
                if (!tableToInit.hasClass('dataTable')) tableToInit.addClass('dataTable');
                try {
                  setTimeout(function () {
                    tableToInit.DataTable({
                      responsive: true,
                      pageLength: 10,
                      destroy: true,
                      language: {
                        search: 'Search:',
                        lengthMenu: 'Show _MENU_ entries',
                        info: 'Showing _START_ to _END_ of _TOTAL_ entries',
                        emptyTable: 'No data available in table',
                        paginate: { first: 'First', last: 'Last', next: 'Next', previous: 'Previous' }
                      }
                    });
                  }, 100);
                } catch (err) { /* no-op */ }
              }
            }
          }).fail(function () {
            subgridModal.find('#subgrid-content').html("<div class='alert alert-danger'>Error refreshing subgrid content. Please contact support.</div>");
          });
        } else {
          subgridModal.find('#subgrid-content').html("<div class='alert alert-warning'>Deleted. Please close and reopen this subgrid.</div>");
        }
      }).fail(function () {
        alert('Error deleting record. Please try again.');
      });
    }
    // else: allow normal navigation for main grid (server redirects back)
  });

  // Nav highlight
  $(document).on('click', '.nav-link', function () {
    document.querySelectorAll('.nav-link').forEach(function (el) {
      el.classList.remove('active', 'bg-gradient', 'text-primary-emphasis', 'shadow-sm');
    });
    this.classList.add('active', 'bg-gradient', 'text-primary-emphasis', 'shadow-sm');
    localStorage.setItem('active-link', this.dataset.id);

    if ($(this).hasClass('dropdown-item')) {
      var parentToggle = $(this).closest('.dropdown').find('.dropdown-toggle')[0];
      if (parentToggle) {
        parentToggle.classList.add('active', 'bg-gradient', 'text-primary-emphasis', 'shadow-sm');
        localStorage.setItem('active-dropdown-parent', parentToggle.dataset.id);
      }
    } else if ($(this).hasClass('dropdown-toggle')) {
      localStorage.setItem('active-dropdown-parent', this.dataset.id);
    } else {
      localStorage.removeItem('active-dropdown-parent');
    }
  });

  var activeId = localStorage.getItem('active-link');
  var activeDropdownParent = localStorage.getItem('active-dropdown-parent');
  document.querySelectorAll('.nav-link').forEach(function (el) {
    el.classList.remove('active', 'bg-gradient', 'text-primary-emphasis', 'shadow-sm');
  });
  if (activeId) {
    var navLink = document.querySelector('.nav-link[data-id="' + activeId + '"]');
    if (navLink) {
      navLink.classList.add('active', 'bg-gradient', 'text-primary-emphasis', 'shadow-sm');
    }
  }
  if (activeDropdownParent) {
    var parentToggle = document.querySelector('.nav-link.dropdown-toggle[data-id="' + activeDropdownParent + '"]');
    if (parentToggle) {
      parentToggle.classList.add('active', 'bg-gradient', 'text-primary-emphasis', 'shadow-sm');
    }
  }
});
