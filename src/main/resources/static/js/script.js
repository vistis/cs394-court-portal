// Login Password Toggle
(function () {
  document.addEventListener('DOMContentLoaded', function () {
    var toggle = document.getElementById('pwToggle');
    var pw = document.getElementById('password');
    if (toggle && pw) {
      toggle.addEventListener('click', function () {
        var show = pw.type === 'password';
        pw.type = show ? 'text' : 'password';
        toggle.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
      });
    }
  });
})();

// Add Participant Modal - Party Type Toggle
(function () {
  var NAME_FIELD_COPY = {
    Individual: { label: "Full Legal Name *", placeholder: "Enter name..." },
    Group: { label: "Organization / Group Name *", placeholder: "Enter organization name..." }
  };

  document.addEventListener('DOMContentLoaded', function () {
    var toggle = document.querySelector('[data-party-type-toggle]');
    if (!toggle) return;

    var hiddenInput = toggle.querySelector('[data-party-type-input]');
    var nameLabel = document.querySelector('[data-party-type-name-label]');
    var nameInput = document.querySelector('[data-party-type-name-input]');
    var options = Array.prototype.slice.call(
      toggle.querySelectorAll('[data-party-type-option]')
    );

    function applyPartyType(partyType) {
      var copy = NAME_FIELD_COPY[partyType] || NAME_FIELD_COPY.Individual;
      options.forEach(function (option) {
        var active = option.getAttribute('data-party-type-option') === partyType;
        option.classList.toggle('segment--active', active);
      });
      if (hiddenInput) hiddenInput.value = partyType;
      if (nameLabel) nameLabel.textContent = copy.label;
      if (nameInput) nameInput.setAttribute('placeholder', copy.placeholder);
    }

    options.forEach(function (option) {
      option.addEventListener('click', function () {
        applyPartyType(option.getAttribute('data-party-type-option'));
      });
    });

    applyPartyType(hiddenInput ? hiddenInput.value : 'Individual');
  });
})();

// Hearing Schedule Form Case Lookup
(function () {
  document.addEventListener('DOMContentLoaded', function () {
    const input = document.getElementById("caseSearch");
    const hidden = document.getElementById("caseId");
    const results = document.getElementById("caseResults");
    if (!input) return;
    let timer;
    let items = [];

    function esc(s) {
      return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
        return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
      });
    }
    function close() {
      if (results) {
        results.classList.remove("open");
        results.innerHTML = "";
      }
    }
    function pick(it) {
      hidden.value = it.caseId;
      input.value = it.caseNumber + " — " + it.title;
      close();
    }
    function render(list) {
      items = list || [];
      if (!items.length) {
        results.innerHTML = '<div class="lookup-empty">No matching cases</div>';
        results.classList.add("open");
        return;
      }
      results.innerHTML = items.map(function (it, i) {
        return '<div class="lookup-item" data-i="' + i + '"><div>' + esc(it.caseNumber) + '</div><div class="lookup-item-title">' + esc(it.title) + "</div></div>";
      }).join("");
      results.classList.add("open");
      results.querySelectorAll(".lookup-item").forEach(function (el) {
        el.addEventListener("mousedown", function (e) {
          e.preventDefault();
          pick(items[+el.dataset.i]);
        });
      });
    }

    input.addEventListener("input", function () {
      hidden.value = "";
      const q = input.value.trim();
      clearTimeout(timer);
      if (q.length < 1) {
        close();
        return;
      }
      timer = setTimeout(function () {
        fetch("/hearings/case-lookup?q=" + encodeURIComponent(q))
          .then(function (r) { return r.json(); })
          .then(render).catch(close);
      }, 200);
    });
    input.addEventListener("blur", function () {
      setTimeout(close, 150);
    });
  });
})();

// Case Participant Combobox
(function () {
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll("[data-combobox]").forEach(combobox => {
      const input = combobox.querySelector("[data-combobox-input]");
      const hiddenInput = combobox.querySelector("input[type='hidden']");
      const options = Array.from(combobox.querySelectorAll("[data-combobox-option]"));
      const empty = combobox.querySelector("[data-combobox-empty]");
      if (!input || !hiddenInput) return;

      const closeMenu = () => combobox.classList.remove("combobox--open");
      const openMenu = () => combobox.classList.add("combobox--open");

      const filterOptions = () => {
        const query = input.value.trim().toLowerCase();
        let matches = 0;
        hiddenInput.value = "";

        if (query === "") {
          options.forEach(option => (option.hidden = true));
          empty.hidden = true;
          closeMenu();
          return;
        }

        options.forEach(option => {
          const text = `${option.dataset.label || ""}`.toLowerCase();
          const matched = text.split(/\s+/).some(word => word.startsWith(query));
          option.hidden = !matched;
          if (matched) { matches += 1; }
        });

        empty.hidden = matches === 0;
        openMenu();
      };

      options.forEach(option => (option.hidden = true));
      empty.hidden = true;

      input.addEventListener("input", filterOptions);
      input.addEventListener("focus", () => {
        if (input.value.trim() !== "") filterOptions();
      });

      options.forEach(option => {
        option.addEventListener("click", () => {
          hiddenInput.value = option.dataset.value;
          input.value = option.dataset.label;
          options.forEach(item => (item.hidden = false));
          empty.hidden = true;
          closeMenu();
        });
      });

      const selected = options.find(option => option.dataset.value === hiddenInput.value);
      if (selected) {
        input.value = selected.dataset.label;
      }

      document.addEventListener("click", event => {
        if (!combobox.contains(event.target)) {
          closeMenu();
        }
      });
    });
  });
})();

// Toolbar Filter Dropdown (e.g. Participants Directory)
(function () {
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll("[data-filter-dropdown]").forEach(dropdown => {
      const toggle = dropdown.querySelector("[data-filter-toggle]");
      if (!toggle) return;

      toggle.addEventListener("click", () => {
        dropdown.classList.toggle("filter-dropdown--open");
      });

      document.addEventListener("click", event => {
        if (!dropdown.contains(event.target)) {
          dropdown.classList.remove("filter-dropdown--open");
        }
      });
    });
  });
})();

// Case Document Form
(function () {
  document.addEventListener('DOMContentLoaded', function () {
    var typeSelect = document.getElementById('documentType');
    var sections = Array.prototype.slice.call(document.querySelectorAll('[data-document-section]'));
    
    if (typeSelect || sections.length > 0) {
      function refreshSections() {
        var selected = typeSelect ? typeSelect.value : 'Filing';
        sections.forEach(function (section) {
          section.hidden = section.getAttribute('data-document-section') !== selected;
        });
      }

      function syncHandlerNames(scope) {
        Array.prototype.slice.call((scope || document).querySelectorAll('.chain-handler')).forEach(function (select) {
          var hidden = select.closest('td').querySelector('.chain-handler-name');
          if (hidden) {
            hidden.value = select.options[select.selectedIndex] ? select.options[select.selectedIndex].text : '';
          }
          select.addEventListener('change', function () {
            if (hidden) {
              hidden.value = select.options[select.selectedIndex] ? select.options[select.selectedIndex].text : '';
            }
          });
        });
      }

      var addButton = document.getElementById('addChainRow');
      if (addButton) {
        addButton.addEventListener('click', function () {
          var body = document.querySelector('#chainTable tbody');
          var first = body.querySelector('tr');
          if (!first) return;
          var row = first.cloneNode(true);
          var index = body.querySelectorAll('tr').length;
          Array.prototype.slice.call(row.querySelectorAll('input, select')).forEach(function (input) {
            input.name = input.name.replace(/chainOfCustody\[\d+\]/, 'chainOfCustody[' + index + ']');
            input.id = input.id ? input.id.replace(/chainOfCustody\d+/, 'chainOfCustody' + index) : input.id;
            if (input.tagName === 'SELECT') {
              input.selectedIndex = 0;
            } else {
              input.value = '';
            }
          });
          body.appendChild(row);
          syncHandlerNames(row);
        });
      }

      if (typeSelect) {
        typeSelect.addEventListener('change', refreshSections);
      }
      refreshSections();
      syncHandlerNames(document);
    }
  });
})();
