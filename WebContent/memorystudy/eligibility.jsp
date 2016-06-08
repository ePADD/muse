<%@page language="java" contentType="text/html; charset=UTF-8"%>
<!DOCTYPE html>
<html dir="ltr" lang ="en" xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta charset="utf-8">
<title>CELL eligibility</title>
	<link rel="stylesheet" href="../css/fonts.css" type="text/css"/>
	<link rel="stylesheet" href="css/memory.css"/>
	<link rel="icon" href="../images/ashoka-favicon.gif">
</head>
<body>
<div class="box">
	<jsp:include page="header.jspf"/>
		<h2 class="title">Eligibility Form</h2>
<!-- This form is a variant of design by Inayaili de León, a web designer and blogger, writing a tutorial about HTML5 forms here: http://24ways.org/2009/have-a-field-day-with-html5-forms/-->
<p>Please fill out the following form to check if you are eligible to participate in this study.</p>

<form action="checkEligible" method = "get">
	<fieldset>
	<legend></legend>
		<fieldset>
		<legend>Gender:</legend>
			<input type="radio" id="gender" name="gender" value="male" required>
			<label for="gender">Male</label>
			<input type="radio" name="gender" value="female" required>
			<label for="gender">Female</label>
		
		</fieldset>

		<br>
				<legend>Age</legend>
				<input type="text" name="age" id="age" name="age" required>
		<br/>
		<br>
			<legend>Please enter your level of education.</legend>
				<select id="education" name="education" size="1">
					<option value="">Choose an option</option>
					<option value="undisclosed">Do not wish to disclose</option>
					<option value="hsdiploma">High School Diploma</option>
					<option value="associates">2-year Associates Degree</option>
					<option value="bachelors">Bachelor's Degree (B.A., B.S., etc.)</option>
					<option value="masters">Master's Degree (M.S., M.B.A, etc.)</option>
					<option value="phd">Doctorate Degree (Ph.D.,M.D., etc.)</option>
				</select>
		<br/>
		<br>
				<legend>Profession</legend>
				<input id="profession" name="profession" type="text" required>
		<br/>
		<br>

			<legend>Please select your ethnicity.</legend>
				<select name="ethnicity" size="1">
					<option value="">Choose an option</option>
					<option value="undisclosed">Do not wish to disclose</option>
					<option value="white">Non-Hispanic White or Euro-American</option>
					<option value="black">Black, Afro-Caribbean, or African American</option>
					<option value="latino">Latino or Hispanic American</option>
					<option value="eastasian">East Asian or Asian American</option>
					<option value="southasian">South Asian or Indian American</option>
					<option value="middleeast">Middle Eastern or Arab American</option>
					<option value="nativeamerican">Native American or Alaskan Native</option>
					<option value="other">Other</option>
				</select>	
		<br/>

	</fieldset>
	<fieldset>
		<p></p>

		<fieldset>
		<legend>Is the majority of your email in English?</legend>
			<input id="emaillang" name="emaillang" type="radio" value="Yes">
			<label for="emaillang">Yes</label>	
			<input name ="emaillang" type="radio" value="No">
			<label for="emaillang">No</label>		
		</fieldset>
		<br/>
		<fieldset>
		<legend>Do you have reliable, uninterrupted Internet access for about an hour?</legend>
			<input type="radio" name="access" value="Yes" required>
			<label id="access" for="access">Yes</label>
			<input type="radio" name="access" value="No" required>
			<label for="access">No</label>
		</fieldset>
		
	</fieldset>
	<br/>
	<div style="text-align:center">
		<button type="submit">Continue</button>
	</div>
</form>
</div>
</body>
</html>
