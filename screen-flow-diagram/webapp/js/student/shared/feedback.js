/**
 * Shared feedback helpers for student pages.
 *
 * Use this file when a student page needs one of the following UI patterns:
 * - showToast: 保存完了、生成完了、画面遷移前の軽い完了通知
 * - showInlineAlert: 未入力、保存失敗、送信失敗、入力不足の警告
 * - showConfirmDialog: 送信前確認、軽いリセット確認、実行前の最終確認
 * - showDangerDialog: 削除、取り消せない操作、影響範囲が大きい操作
 *
 * Do not use toast for destructive confirmation.
 * Do not use confirm dialog for simple success feedback.
 */
(function() {
  const rootId = 'ppe-feedback-root';
  const toastHostId = 'ppe-feedback-toast-host';
  const modalId = 'ppe-feedback-modal';

  function ensureRoot() {
    let root = document.getElementById(rootId);
    if (root) return root;

    root = document.createElement('div');
    root.id = rootId;
    root.innerHTML = '' +
      '<div id="' + toastHostId + '" class="toast-container position-fixed bottom-0 end-0 p-3"></div>' +
      '<div class="modal fade ppe-feedback-modal" id="' + modalId + '" tabindex="-1" aria-hidden="true">' +
        '<div class="modal-dialog modal-dialog-centered">' +
          '<div class="modal-content">' +
            '<div class="modal-header">' +
              '<div>' +
                '<div class="ppe-feedback-kicker d-none" data-feedback-kicker></div>' +
                '<h5 class="modal-title" data-feedback-title>確認</h5>' +
              '</div>' +
              '<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>' +
            '</div>' +
            '<div class="modal-body">' +
              '<p class="mb-0" data-feedback-message></p>' +
              '<div class="ppe-feedback-detail-block d-none mt-3" data-feedback-detail-block>' +
                '<div class="ppe-feedback-detail-title" data-feedback-detail-title></div>' +
                '<div class="ppe-feedback-detail-list" data-feedback-details></div>' +
              '</div>' +
            '</div>' +
            '<div class="modal-footer">' +
              '<button type="button" class="btn btn-outline-secondary" data-feedback-cancel data-bs-dismiss="modal">キャンセル</button>' +
              '<button type="button" class="btn btn-primary" data-feedback-confirm>実行する</button>' +
            '</div>' +
          '</div>' +
        '</div>' +
      '</div>';

    document.body.appendChild(root);
    return root;
  }

  function normalizeElement(target) {
    if (!target) return null;
    if (typeof target === 'string') {
      return document.querySelector(target);
    }
    return target;
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function toastHeaderClass(variant) {
    switch (variant) {
      case 'danger':
        return 'text-bg-danger';
      case 'warning':
        return 'text-bg-warning';
      case 'info':
        return 'text-bg-info';
      case 'success':
        return 'text-bg-success';
      default:
        return 'text-bg-primary';
    }
  }

  function confirmButtonClass(variant) {
    switch (variant) {
      case 'danger':
        return 'btn-danger';
      case 'warning':
        return 'btn-warning';
      case 'info':
        return 'btn-info';
      case 'success':
        return 'btn-success';
      default:
        return 'btn-primary';
    }
  }

  /**
   * Use for lightweight completion feedback.
   * Examples: 保存しました、更新しました、評価例を生成しました。
   */
  function showToast(options) {
    ensureRoot();

    const settings = options || {};
    const title = settings.title || '通知';
    const message = settings.message || '';
    const variant = settings.variant || 'primary';
    const delay = typeof settings.delay === 'number' ? settings.delay : 1800;
    const toastHost = document.getElementById(toastHostId);
    if (!toastHost) return null;

    const toastEl = document.createElement('div');
    toastEl.className = 'toast border-0 shadow-lg ppe-feedback-toast';
    toastEl.setAttribute('role', 'status');
    toastEl.setAttribute('aria-live', 'polite');
    toastEl.setAttribute('aria-atomic', 'true');
    toastEl.innerHTML = '' +
      '<div class="toast-header ' + toastHeaderClass(variant) + '">' +
        '<strong class="me-auto">' + escapeHtml(title) + '</strong>' +
        '<button type="button" class="btn-close' + (variant === 'warning' ? '' : ' btn-close-white') + '" data-bs-dismiss="toast" aria-label="閉じる"></button>' +
      '</div>' +
      '<div class="toast-body">' + escapeHtml(message) + '</div>';

    toastHost.appendChild(toastEl);

    if (!window.bootstrap || !bootstrap.Toast) {
      window.setTimeout(() => toastEl.remove(), delay);
      return toastEl;
    }

    const toast = bootstrap.Toast.getOrCreateInstance(toastEl, { delay: delay });
    toastEl.addEventListener('hidden.bs.toast', () => {
      toastEl.remove();
    }, { once: true });
    toast.show();
    return toast;
  }

  /**
   * Use for warnings or errors that should stay near the current form or panel.
   * Examples: 未入力があります、保存に失敗しました、送信できませんでした。
   */
  function showInlineAlert(target, options) {
    const host = normalizeElement(target);
    if (!host) return null;

    const current = host.querySelector(':scope > .ppe-inline-alert');
    if (current) current.remove();

    const settings = options || {};
    const variant = settings.variant || 'warning';
    const message = settings.message || '';
    const alertEl = document.createElement('div');
    alertEl.className = 'alert alert-' + variant + ' ppe-inline-alert';
    alertEl.setAttribute('role', 'alert');
    alertEl.innerHTML = escapeHtml(message);
    host.prepend(alertEl);
    return alertEl;
  }

  function clearInlineAlert(target) {
    const host = normalizeElement(target);
    if (!host) return;
    const current = host.querySelector(':scope > .ppe-inline-alert');
    if (current) current.remove();
  }

  /**
   * Use for confirmation before an important action.
   * Examples: 送信する前、再評価を始める前、入力をリセットする前。
   * Prefer this over window.confirm when you need context, details, or consistent design.
   */
  function showConfirmDialog(options) {
    ensureRoot();

    const settings = options || {};
    const title = settings.title || '確認';
    const message = settings.message || '';
    const details = Array.isArray(settings.details) ? settings.details : [];
    const hasDetailTitle = Object.prototype.hasOwnProperty.call(settings, 'detailTitle');
    const detailTitle = hasDetailTitle ? settings.detailTitle : null;
    const confirmLabel = settings.confirmLabel || '実行する';
    const cancelLabel = settings.cancelLabel || 'キャンセル';
    const kicker = settings.kicker || '';
    const variant = settings.variant || 'primary';

    if (!window.bootstrap || !bootstrap.Modal) {
      return Promise.resolve(window.confirm(message || title));
    }

    const modalEl = document.getElementById(modalId);
    const titleEl = modalEl.querySelector('[data-feedback-title]');
    const kickerEl = modalEl.querySelector('[data-feedback-kicker]');
    const messageEl = modalEl.querySelector('[data-feedback-message]');
    const detailBlockEl = modalEl.querySelector('[data-feedback-detail-block]');
    const detailTitleEl = modalEl.querySelector('[data-feedback-detail-title]');
    const detailsEl = modalEl.querySelector('[data-feedback-details]');
    const cancelButton = modalEl.querySelector('[data-feedback-cancel]');
    const confirmButton = modalEl.querySelector('[data-feedback-confirm]');
    const modalContent = modalEl.querySelector('.modal-content');

    titleEl.textContent = title;
    messageEl.textContent = message;
    cancelButton.textContent = cancelLabel;
    confirmButton.textContent = confirmLabel;
    confirmButton.className = 'btn ' + confirmButtonClass(variant);
    modalContent.classList.toggle('ppe-feedback-modal-danger', variant === 'danger');

    if (kicker) {
      kickerEl.textContent = kicker;
      kickerEl.classList.remove('d-none');
    } else {
      kickerEl.textContent = '';
      kickerEl.classList.add('d-none');
    }

    if (details.length > 0) {
      if (detailTitle === '') {
        detailTitleEl.textContent = '';
        detailTitleEl.classList.add('d-none');
      } else {
        detailTitleEl.textContent = detailTitle || '次の内容を確認してください。';
        detailTitleEl.classList.remove('d-none');
      }
      detailsEl.innerHTML = '<ul class="ppe-feedback-detail-bullets mb-0">' + details.map((detail) => '<li class="ppe-feedback-detail-item">' + escapeHtml(detail) + '</li>').join('') + '</ul>';
      detailBlockEl.classList.remove('d-none');
    } else {
      detailTitleEl.textContent = '';
      detailTitleEl.classList.add('d-none');
      detailsEl.innerHTML = '';
      detailBlockEl.classList.add('d-none');
    }

    return new Promise((resolve) => {
      const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
      let handled = false;

      function cleanup() {
        confirmButton.removeEventListener('click', onConfirm);
        modalEl.removeEventListener('hidden.bs.modal', onHidden);
      }

      function onConfirm() {
        handled = true;
        cleanup();
        modal.hide();
        resolve(true);
      }

      function onHidden() {
        cleanup();
        if (!handled) {
          resolve(false);
        }
      }

      confirmButton.addEventListener('click', onConfirm);
      modalEl.addEventListener('hidden.bs.modal', onHidden);
      modal.show();
    });
  }

  /**
   * Use for destructive or irreversible actions.
   * Examples: 削除、一括反映、取り消せない更新、提出の確定。
   */
  function showDangerDialog(options) {
    const settings = Object.assign({}, options || {}, {
      variant: 'danger',
      kicker: (options && options.kicker) || '取り消しできない操作'
    });
    return showConfirmDialog(settings);
  }

  /**
   * Use when a page repeatedly needs the same title or inline alert target.
   * This keeps page scripts focused on messages and details instead of wrapper boilerplate.
   */
  function createPageFeedback(options) {
    const settings = options || {};
    const defaultTitle = settings.title || '通知';
    const defaultVariant = settings.variant || 'primary';
    const alertTarget = settings.alertTarget || settings.inlineAlertTarget || null;

    function resolveTarget(targetOverride) {
      const source = typeof targetOverride !== 'undefined' ? targetOverride : alertTarget;
      if (!source) return null;
      return normalizeElement(typeof source === 'function' ? source() : source);
    }

    return {
      toast(input) {
        if (typeof input === 'string') {
          return showToast({
            title: defaultTitle,
            message: input,
            variant: defaultVariant
          });
        }

        return showToast(Object.assign({
          title: defaultTitle,
          variant: defaultVariant
        }, input || {}));
      },

      inlineAlert(message, variant, targetOverride) {
        const target = resolveTarget(targetOverride);
        if (!target) {
          window.alert(message);
          return null;
        }

        return showInlineAlert(target, {
          message: message,
          variant: variant || 'warning'
        });
      },

      clearInlineAlert(targetOverride) {
        const target = resolveTarget(targetOverride);
        if (target) {
          clearInlineAlert(target);
        }
      },

      confirm(dialogOptions) {
        return showConfirmDialog(dialogOptions);
      },

      danger(dialogOptions) {
        return showDangerDialog(dialogOptions);
      }
    };
  }

  window.PPEFeedback = Object.assign({}, window.PPEFeedback, {
    showToast: showToast,
    showInlineAlert: showInlineAlert,
    clearInlineAlert: clearInlineAlert,
    showConfirmDialog: showConfirmDialog,
    showDangerDialog: showDangerDialog,
    createPageFeedback: createPageFeedback
  });
})();