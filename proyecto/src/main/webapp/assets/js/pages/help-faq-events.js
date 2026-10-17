(function () {
    var search = document.getElementById('faq-search');
    var items = Array.from(document.querySelectorAll('.accordion-item'));
    var category = '';
    function filter() {
        var query = search.value.trim().toLowerCase();
        var count = 0;
        items.forEach(function (item) {
            var visible = (!category || item.dataset.category === category) &&
                (!query || item.textContent.toLowerCase().includes(query));
            item.hidden = !visible;
            if (visible) count++;
        });
        document.getElementById('faq-empty').hidden = count !== 0;
    }
    items.forEach(function (item) {
        var button = item.querySelector('button');
        button.addEventListener('click', function () {
            var open = button.getAttribute('aria-expanded') !== 'true';
            button.setAttribute('aria-expanded', String(open));
            item.classList.toggle('active', open);
            item.querySelector('.accordion-content').hidden = !open;
        });
    });
    search.addEventListener('input', function () { category = ''; filter(); });
    document.querySelectorAll('[data-faq-category]').forEach(function (button) {
        button.addEventListener('click', function () {
            category = category === button.dataset.faqCategory ? '' : button.dataset.faqCategory;
            search.value = '';
            filter();
        });
    });
    document.getElementById('faq-support').addEventListener('click', function () {
        var note = document.getElementById('faq-support-note');
        note.hidden = !note.hidden;
        this.setAttribute('aria-expanded', String(!note.hidden));
    });
})();
