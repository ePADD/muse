<script src="jquery/jquery.js"></script>
<script src="jquery/jquery-ui.js"></script>

<ul class="personList">
<li class="person">AAAA</li>
<li class="person">BBBB</li>
</ul>
<p/>
<ul class="personList">
<li class="person">CCCC</li>
<li class="person">DDDD</li>
</ul>

<script>
	$('.personList').each (function() {
			$(this).sortable(
			{
				start: startDND,
				stop: stopDND,
				receive: receiveDND,
				connectWith: '.personList',
				items: 'li.person'
			});
			$(this).disableSelection();
	});

	function startDND() { }
	function stopDND() { }
	function receiveDND() { }
</script>
