let page = 0;
let pageSize = 10;
let isFreshSearch = true;
let currentPageElement = document.getElementById('currentPage');
let lastPageElement = document.getElementById('lastPage');
let lastPage = 0;

// TODO implement a more sophisticated page navigation system where a button disappears if its not possible to go further or backwards

function performSearch() {
    let formData = $('#operationForm').serialize() + '&page=' + page + '&pageSize=' + pageSize + '&isFreshSearch=' + isFreshSearch;

    $.ajax({
        type: 'GET',
        url: '/api/search',
        data: formData,
        success: function(data) {
            // when no results are found
            if (data == null || data.length === 0) {
                $('#operationResults').html("No results found!");
                return;
            }

            // get the last page that comes in the response from the backend
            lastPage = parseInt(data.pop()[0], 10);
            lastPageElement.innerHTML = (lastPage + 1).toString();

            // clear existing results
            $('#operationResults').empty();

            data.forEach(function(urlInfo){
                if(urlInfo == null || urlInfo[0] == null) return;

                console.log(urlInfo);

                let urlDiv = $('<div class="search-result-item flex flex-col justify-left p-6"></div>');
                urlDiv.append($('<a></a>').attr('href', urlInfo[0]).text(urlInfo[0]));
                urlInfo.forEach(function(item){
                    urlDiv.append($('<p></p>').text(item));
                });

                $('#operationResults').append(urlDiv);
            });

            if (isFreshSearch) {
                page = 0; // reset to first page if it's a fresh search
            }
            isFreshSearch = false; // reset flag after initial load
        },
        error: function() {
            $('#operationResults').html('<p>An error has occurred</p>');
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

$('#operationForm').submit(function(event) {
    event.preventDefault();
    console.log(window.operationType);
    if(window.operationType !== "search") return;
    let query = document.getElementById('operationQuery').value;
    console.log(query);
    if(query === "") return;

    isFreshSearch = true;
    performSearch();
    document.getElementById('pageNavigators').classList.remove('hidden');
    fetchHackerNews(query);
    currentPageElement.innerHTML = (page + 1).toString();
});


async function fetchHackerNews(query){
    let url = `/api/hacker-news?query=${encodeURIComponent(query)}`;

    let hackerNewsMatchingNewsDiv = $('<div class="flex flex-col justify-left p-6"></div>');

    // TODO make a fancy loading animation instead of just text
    let hackerNewsStoriesDiv = document.getElementById('hackerNewsStories');
    hackerNewsStoriesDiv.append($('<p></p>').text('Fetching Hacker News stories...'));

    try{
        const response = await fetch(url);
        const data = await response.json();

        // clear any existing results
        hackerNewsStoriesDiv.empty();

        // check if data is available
        if (data == null || data.length === 0) {
            hackerNewsStoriesDiv.html('<p>No matching Hacker News stories found.</p>');
            return;
        }

        hackerNewsMatchingNewsDiv.append($('<p></p>').text('Found ' + data.length + '  Hacker News stories matching your search.'));
        hackerNewsMatchingNewsDiv.append($('<button></button>').text('Index pages'));
        hackerNewsStoriesDiv.append(hackerNewsMatchingNewsDiv);

    } catch(error){
        console.error('Error fetching Hacker News data: ', error);
    }
}