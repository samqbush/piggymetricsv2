var global = {
    mobileClient: false,
    savePermit: true,
    usd: 0,
    eur: 0
};

/**
 * Oauth2 (Phase 5 BFF)
 *
 * Authentication is handled by the gateway (Backend-for-Frontend): the browser
 * holds only an HttpOnly session cookie and never sees the access token. Login is
 * a full-page redirect to the gateway's authorization_code flow; the gateway relays
 * the JWT downstream. No token is stored in localStorage anymore.
 */

function startLogin() {
	window.location = 'oauth2/authorization/piggymetrics';
}

function getCookie(name) {
	var match = document.cookie.match('(^|;)\\s*' + name + '\\s*=\\s*([^;]+)');
	return match ? match.pop() : '';
}

// Double-submit CSRF: echo the non-HttpOnly XSRF-TOKEN cookie set by the gateway
// back as the X-XSRF-TOKEN header on every state-changing request.
$.ajaxSetup({
	beforeSend: function (xhr, settings) {
		if (!/^(GET|HEAD|OPTIONS|TRACE)$/i.test(settings.type)) {
			var csrf = getCookie('XSRF-TOKEN');
			if (csrf) {
				xhr.setRequestHeader('X-XSRF-TOKEN', csrf);
			}
		}
	}
});

/**
 * Current account
 */

function getCurrentAccount() {

	var account = null;

	$.ajax({
		url: 'accounts/current',
		dataType: 'json',
		type: 'get',
		async: false,
		success: function (data) {
			// A followed login redirect returns HTML (a string); only a JSON object
			// is a real, authenticated account.
			if (data && typeof data === 'object') {
				account = data;
			}
		}
	});

	return account;
}

$(window).load(function(){

	if(/Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent) ) {
		FastClick.attach(document.body);
        global.mobileClient = true;
	}

    $.getJSON("https://api.exchangeratesapi.io/latest?base=RUB&symbols=EUR,USD", function( data ) {
        global.eur = 1 / data.rates.EUR;
        global.usd = 1 / data.rates.USD;
    });

	var account = getCurrentAccount();

	if (account) {
		showGreetingPage(account);
	} else {
		showLoginForm();
	}
});

function showGreetingPage(account) {
    initAccount(account);
	var userAvatar = $("<img />").attr("src","images/userpic.jpg");
	$(userAvatar).load(function() {
		setTimeout(initGreetingPage, 500);
	});
}

function showLoginForm() {
	$("#loginpage").show();
	$("#frontloginform").focus();
	setTimeout(initialShaking, 700);
}