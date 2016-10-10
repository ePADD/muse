<html>

<span id="test">
This is a string with &#039;quotes &#034;. Ok.
</span>


<script type="text/javascript">
var x = "<html><body><span id=\"myid\">ABC</span";
alert (x.getElementById('myid'));
alert (x.getElementById('noid'));

// alert (document.getElementById('test').value);
</script>
</html>