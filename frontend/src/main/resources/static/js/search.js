$(document).ready(function() {
    let page = 0;
    let pageSize = 10;
    let isFreshSearch = true;
    let currentPageElement = document.getElementById('currentPage');
    let lastPageElement = document.getElementById('lastPage');
    let lastPage = 0;

    function performSearch() {
        let formData = $('#searchForm').serialize() + '&page=' + page + '&pageSize=' + pageSize + '&isFreshSearch=' + isFreshSearch;

        $.ajax({
            type: 'GET',
            url: '/search',
            data: formData,
            success: function(data) {
                // when no results are found
                if (data == null || data.length === 0) {
                    $('#searchResults').html("No results found!");
                    return;
                }

                // get the last page that comes in the response from the backend
                lastPage = parseInt(data.pop()[0], 10);
                lastPageElement.innerHTML = (lastPage + 1).toString();

                // clear existing results
                $('#searchResults').empty();

                data.forEach(function(urlInfo){
                    let urlDiv = $('<div class="search-result-item flex flex-col justify-left p-6"></div>');
                    urlInfo.forEach(function(item){
                        urlDiv.append($('<p></p>').text(item));
                    });
                    $('#searchResults').append(urlDiv);
                });

                if (isFreshSearch) {
                    page = 0; // reset to first page if it's a fresh search
                }
                isFreshSearch = false; // reset flag after initial load
            },
            error: function() {
                $('#searchResults').html('<p>An error has occurred</p>');
            }
        });
    }

    $('#nextPage').click(function() {
        if(page === lastPage) return;
        ++page;
        currentPageElement.innerHTML = (page + 1).toString();
        performSearch();
    });

    $('#previousPage').click(function() {
        if(page <= 0) return;
        --page;
        currentPageElement.innerHTML = (page + 1).toString();
        performSearch();
    });

    $('#searchForm').submit(function(event) {
        isFreshSearch = true;
        event.preventDefault();
        performSearch();
        currentPageElement.innerHTML = (page + 1).toString();
    });


});