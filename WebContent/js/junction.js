function _JX(config){ 
	// See end of file for Module instantiation.
	var _config = { autorefresh: true };
	if (config) {
		if (typeof(config) == "string") {
			_config.host = config;
		} else {
		        // TODO: extend
			_config = config;
		}
	}
	
	// TODO: make the logger more configurable
	this.logError = function(msg) {
   	  if(typeof console != 'undefined'){
	    console.error(msg);
	  }
	}
	
	this.logInfo = function(msg) {
	  if(typeof console != 'undefined'){
	    console.info(msg);
          }
	}
	
	/**
	 * Create a new Junction instance.
	 * TODO: document the necessary properties of activity and actor..
	 */
	this.newJunction = function(activity, actor) {
		var _sessionID = null;
		var _hostURL = null;
		var _role = null;
		var _isActivityCreator = false;
		var query = parseUri(window.location).query;
		var i;
                var inviteInURL = false;
                
                
                if ((i = query.indexOf('jxuri=')) >= 0) {
                  inviteInURL = true;
                  var invite = query.substr(i+6);
                  if ((i = invite.indexOf('&')) >= 0) {
                    invite = invite.substr(0,i);
                  }
                  invite = decodeURIComponent(invite);
                  var parsed = parseUri(invite);
                  _sessionID = parsed.path.substring(1);
                  _hostURL = parsed.host;
                  if (parsed.queryKey.role) {
                    _role = parsed.query.substr(parsed.query.indexOf("role=")+5);
                    if ((i = _role.indexOf("&")) > 0) {
                      _role = _role.substr(0,i);
                    }
                  }
                }
		else if (activity) {
			if (activity.sessionID) {
				_sessionID = activity.sessionID;
			}
			if (activity.host) {
				_hostURL = activity.host;
			}
		}

		if (!_sessionID) {
			_sessionID = randomUUID();
			_isActivityCreator = true;
		}
		if (!_hostURL) {
			if (_config.host) {
				_hostURL = _config.host;
			}
		}
		
                if (!inviteInURL && _config.autorefresh) {
                  var uri = "junction://" + _hostURL + "/" + _sessionID;
                  var url = getPageWithURI(uri);
                  window.location = url;
                }
		var _actorID = randomUUID();
		
		if (typeof(actor) != "object") {
		  if (typeof(actor) == "undefined") {
		    if (_role) {
		      actor = _role;
		    } else if (activity.defaultRole) {
		      if (typeof(activity.defaultRole) == "string") {
  		        actor = activity.defaultRole;
  		      } else if (typeof(activity.defaultRole) == "object") {
  		        var def = activity.defaultRole;
  		        var plat = JX.getThisPlatformType();
  		        if (def[plat]) {
  		          actor = def[plat];
  		        } else if (def["default"]) {
  		          actor = def["default"];
  		        }
  		      }
		    }
		  }

		  if (typeof(actor) == "string") {
		    // use role spec
		    if (typeof(activity.roles[actor]) == "undefined") {
		      this.logError("No specification for role " + actor);
		      return false;
		    }
		    if (typeof(activity.roles[actor].platforms.web.code) != "undefined") {
  		      actor = activity.roles[actor].platforms.web.code;
  		    } else {
  		      this.logError("No codebase found for " + actor);
  		      return false;
  		    }
		  }
		}
		
		var _jx = new JX.Junction(actor, _actorID, activity, _sessionID, _hostURL, _isActivityCreator);

		return _jx;

	};
	
	this.getThisPlatformType = function() {
	  var ua = navigator.userAgent.toLowerCase();
	  if (ua.indexOf("mobile") > 0) return "phone";
	  return "pc";
	}

        var getPageWithURI = function(uri) {
          var url = window.location.toString();
          if (url.indexOf("?") > 0) {
            url += "&jxuri=" + encodeURIComponent(uri);
          } else {
            url += "?jxuri=" + encodeURIComponent(uri);
          }
          return url;
        }
        
        
	/**
	 * Create and return a new BOSH connection to a remote XMPP host.
	 */
	var createXMPPConnection = function(hostURL, onConnect) {
		var _jid='junction';
		var _pw='junction';
		var connection = new Strophe.Connection('http://' + hostURL + '/http-bind');
		connection.connect(_jid, _pw, onConnect);
		return connection;
	};


	/**
	 * Class: Junction
	 * 
	 * The principle class describing a Junction activity. 
	 * Instances should be created with JX.newJunction.
	 */
	this.Junction = Class.extend(
		{

			init: function(actor, actorID, activityDesc, sessionID, hostURL, isActivityCreator) {

				this.xmppConnection = null; // See below..
				this.activityDesc = activityDesc;
				this.sessionID = sessionID;
				this.hostURL = hostURL;
				this.actor = actor;
				this.actorID = actorID;
				this.isActivityCreator = isActivityCreator;
				this.extrasDirector = new JX.ExtrasDirector();

				this.MUC_ROOM = sessionID;
				this.MUC_COMPONENT = 'conference.' + hostURL;

				this.registerActor(this.actor);

				// Init the connection!
				var self = this;
				this.xmppConnection = createXMPPConnection(
					this.hostURL, 
					function(status){ return self._onConnect(status);});

			},


			registerActor: function(actor){
				var self = this;
				
				// Initialize the client-facing actor interface.
				// This is the principle means with which a Junction 
				// client interacts with an Activity.
				(function(actor){

					 actor.junction = self;

					 actor.leave = function() { self.disconnect(); };

					 actor.sendMessageToActor = function(actorID, msg) {
						 self.sendMessageToActor(actorID, msg);
					 };

					 actor.sendMessageToRole = function(role, msg) {
						 self.sendMessageToRole(role, msg);
					 };

					 actor.sendMessageToSession = function(msg) {
						 self.sendMessageToSession(msg);
					 };

				 })(this.actor);


				// Register all the JunctionExtra instances provided
				// by this actor.
				var extras = self.actor.initialExtras;
				if(extras != null){
					for (var i = 0; i < extras.length;  i++) {
						self.registerExtra(extras[i]);
					}
				}

			},

			logInfo: function(msg){
				JX.logInfo(msg);
			},
			
			logError: function(msg){
				JX.logError(msg);
			},

			registerExtra: function(extra) {
				extra.setActor(this.actor);
				this.extrasDirector.registerExtra(extra);
			},

			getSessionID: function() { return this.sessionID; },

			sendMessageToActor: function(actorID, msg) {
				if (this.extrasDirector.beforeSendMessageToActor(actorID, msg)) {
					this.doSendMessageToActor(actorID, msg);
				}
			},

			doSendMessageToActor: function(actorID, msg){
				if (!(typeof msg == 'object')) {
					msg = {v:msg};
				}
				msg = JSON.stringify(msg);
				this.xmppConnection.send(
					$msg({to: this.MUC_ROOM + "@" + this.MUC_COMPONENT + '/' + actorID,
						  type: "chat", id: this.xmppConnection.getUniqueId()
						 }).c("body")
						.t(msg).up()
						.c("nick", {xmlns: "http://jabber.org/protocol/nick"})
						.t(actorID).tree());
			},


			sendMessageToRole: function (role, msg) {
				if(this.extrasDirector.beforeSendMessageToRole(role, msg)){
					this.doSendMessageToRole(role, msg);
				}
			},

			doSendMessageToRole: function (role, msg) {
				if (!(typeof msg == 'object')) {
					msg = {v:msg};
				}
				if (msg.jx) {
					msg.jx.targetRole = role;
				} 
				else {
					msg.jx = { targetRole: role };
				}
				msg = JSON.stringify(msg);
				this.xmppConnection.send($msg({to: this.MUC_ROOM + "@" + this.MUC_COMPONENT,
											   type: "groupchat", id: this.xmppConnection.getUniqueId()
											  }).c("body")
										 .t(msg).up()
										 .c("nick", {xmlns: "http://jabber.org/protocol/nick"})
										 .t(this.actorID).tree());
			},


			sendMessageToSession: function (msg) {
				if (this.extrasDirector.beforeSendMessageToSession(msg)) {
					this.doSendMessageToSession(msg);
				}
			},

			doSendMessageToSession: function(msg){
				if (!(typeof msg == 'object')) {
					msg = {v:msg};
				}
				msg = JSON.stringify(msg);
				this.xmppConnection.send(
					$msg({to: this.MUC_ROOM + "@" + this.MUC_COMPONENT,
						  type: "groupchat", id: this.xmppConnection.getUniqueId()
						 }).c("body")
						.t(msg).up()
						.c("nick", {xmlns: "http://jabber.org/protocol/nick"})
						.t(this.actorID).tree());
			},


			// receive
			triggerMessageReceived: function(header, message) {
				if (this.extrasDirector.beforeOnMessageReceived(header,message)) {
					this.actor.onMessageReceived(message, header);
					this.extrasDirector.afterOnMessageReceived(header,message);
				}
			},
			
			triggerActorJoin: function(isCreator){
				// Create
				if (isCreator) {
					if (!this.extrasDirector.beforeActivityCreate()) {
						this.disconnect();
						return;
					}
					this.actor.onActivityCreate();
					this.extrasDirector.afterActivityCreate();
				}
				
				// Join
				if (!this.extrasDirector.beforeActivityJoin()) {
					this.disconnect();
					return;
				}
				this.actor.onActivityJoin();
				this.extrasDirector.afterActivityJoin();
			},

			getInvitationURI: function() {
				var params = {};
				var url = 'junction://' + this.hostURL + "/" + this.sessionID;
				if (arguments[0]) {
					params["role"] = arguments[0];
				}
				
				this.extrasDirector.updateInvitationParameters(params);
				var args = '';
				for(var name in params){
					args += "&" + name + "=" + params[name];
				}
				if (args.length > 0){
				  url += "?" + args.substr(1);
				}
				return url;
			},

			getInvitationForWeb: function(role) { // TODO: add role parameter
				// TODO: AcSpec should be { roles: { "player": { ... } } }
				var url='';
				if (role && this.activityDesc.roles) {
					if (this.activityDesc.roles[role]) {
						var plat=this.activityDesc.roles[role].platforms;
						if (plat["web"]) {
							url = plat["web"].url.toString();
							if (url.indexOf("?") > 0) {
							  url = url.substr(0,url.indexOf("?"));
							}
						}
					}
					if (url=='') url=document.location.toString(); // return false?
				} else {
					url=document.location.toString();
				}
				var params = 'jxuri=' + encodeURIComponent(this.getInvitationURI(role));
				if (url.indexOf('?')>0) {
					return url + '&' + params;
				} else {
					return url + '?' + params;
				}
			},

			getInvitationQR: function () {
				var url;
				var size;
    			//var content = new Object();
				//content.sessionID = _sessionID;
				//content.host = _hostURL;
				//content.ad = _activityDesc;

				if (arguments.length == 0) {
					url = 'junction://' + this.hostURL + "/" + this.sessionID;
				} else if (arguments[0] != false) {
					url = 'junction://' + this.hostURL + "/" + this.sessionID + "?role="+arguments[0];
					//content.role = arguments[0];
				}

				var options = {text: url};
				if (arguments.length == 2) {
					options.width = 250;
					options.height = 250;
				} else {
					options.width = arguments[1];
					options.height = arguments[1];
				}
				return createQRCanvas(options);	
			},

			getQRForWeb: function(size, role) {
				var url = this.getInvitationForWeb(role);
				var options = {text: url};
				if (!size) {
					options.width = 250;
					options.height = 250;
				} else {
					options.width = size;
					options.height = size;
				}

				return createQRCanvas(options);			
			},

			getActorsForRole: function() {},

			getRoles:  function() {},

			disconnect: function() { this.xmppConnection.disconnect(); },


			/*******  Strophe XMPP Handlers  **********/

			_onPresence: function(msg){

				var from = Strophe.getResourceFromJid(msg.getAttribute('from'));
				var type = msg.getAttribute('type');

				// Are we the owner of this room?
				if (type == null && from == this.actorID) {
					var roomdesc = "";
					try {
					  roomdesc = JSON.stringify(this.activityDesc);
					} catch (e) {
					  this.logError(e);
					}

					// Unlock room
					var form = $iq({to: this.MUC_ROOM + "@" + this.MUC_COMPONENT,type: 'set'})
						.c("query", {xmlns: "http://jabber.org/protocol/muc#owner"})
						.c("x", {xmlns: "jabber:x:data", type:"submit"})
						.c("field", {"var": "muc#roomconfig_roomdesc"})
						.c("value").t(roomdesc)
 						.up().up()
						.c("field", {"var": "muc#roomconfig_whois"})
						.c("value").t("moderators")
						.up().up()
					//.c("field", {"var": "muc#roomconfig_publicroom"})
					//.c("value").t("0")
						.tree();

					this.xmppConnection.send(form);

					if (this.isActivityCreator) {
						var roles = this.activityDesc.roles;
						if (typeof(roles) == 'object') {
							for (r in roles){
								var plats = roles[r].platforms;
								if(plats['jxservice']){
									var uri = this.getInvitationURI(r);
									JX.inviteServiceForRole(uri, this.activityDesc, r);
								}
							}
						}
					}
				}

				// TODO: 
				// Making assumption here that any presence msg must have come from
				// ourself - since we always supply the 'to' attribute when sending
				// presence.
				if (this.actor) {
					this.actor.actorID = this.actorID;
					if (this.isActivityCreator && this.actor.onActivityCreate) {
						this.triggerActorJoin(true);
					}
					if (this.actor.onActivityJoin) {
						this.triggerActorJoin(false);
					}
				}

				return false;
			},


			_onMessage: function(msg){
				var from = msg.getAttribute('from');
				var i = from.lastIndexOf('/');
				if (i >= 0) {
					from = from.substring(i+1);
				}
				var type = msg.getAttribute('type');
				if (type == "error") {
					console.info("Error: " + msg);
					return;
				}

				var body = msg.getElementsByTagName("body")[0].childNodes[0];

				var jxheader = new Object();
				jxheader.from = from;

				if ((type == "groupchat" || type == "chat") && body) {
					try {
						var txt = body.wholeText;
						if(txt.match(/^This room/)){
							this.logInfo(txt);
						}
						else{
							var content = JSON.parse(body.wholeText);
							if (content.jx && content.jx.targetRole) {
								if (!this.actor.roles) {
									return true;
								}
								// Otherwise pass message to Actor
								for (i=0;i<this.actor.roles.length;i++) {
									if (this.actor.roles[i] == content.jx.targetRole) {
										this.triggerMessageReceived.onMessageReceived(jxheader, content);
										return true;
									}
								}
								return true;
							}
							this.triggerMessageReceived(jxheader, content);
						}
					} 
					catch (e) {
						this.logError("Failed to handle msg: '" + body.wholeText + "'   " + e.message);
					}
				}
				return true;
			},


			_onConnect: function(status){
				var self = this;

				if (status == Strophe.Status.CONNECTED) {
					var old = window.onbeforeunload;
					var discon =
						function() {
							this.xmppConnection.disconnect();
						};
					if (typeof window.onbeforeunload != 'function') {
						window.onbeforeunload = discon;
					} else {
						window.onbeforeunload = function() {
							old();
							discon();
						};
					}

					this.xmppConnection.send(
						$pres({to: this.MUC_ROOM + "@" + this.MUC_COMPONENT + "/" + this.actorID})
							.c("x", {xmlns: "http://jabber.org/protocol/muc"})
							.tree());
					
					this.xmppConnection.addHandler(function(msg){ return self._onPresence(msg); },
												   null,
												   'presence',
												   null,null,null);
					if (this.actor && this.actor.onMessageReceived) {
						this.xmppConnection.addHandler(function(msg){ return self._onMessage(msg); },
													   null,
													   'message',
													   null,null,null);
					}
				}
			}

		});




	/**
	 *  Class: JunctionExtra
	 * 
	 *  A Junction plugin. Allows clients to inject code before or after message sending,
	 *  activity joining, activity creation, etc.
	 */
	this.JunctionExtra = Class.extend(
		{
			init: function(){
				this.actor = null;	
			},

			/**
			 * This method should only be called internally.
			 * @param actor
			 */
			setActor: function(actor) {
				this.actor = actor;
			},

			/**
			 * Update the parameters that will be sent in an invitation
			 */
			updateInvitationParameters: function(params) {},

			/**
			 * Returns true if the normal event handling should proceed;
			 * Return false to stop cascading.
			 */
			beforeOnMessageReceived: function(messageHeader, jsonMsg) { return true; },

			afterOnMessageReceived: function(messageHeader, jsonMsg) {},
			beforeSendMessageToActor: function(actorID, jsonMsg) { return this.beforeSendMessage(jsonMsg); },
			beforeSendMessageToRole: function(role, jsonMsg) { return this.beforeSendMessage(jsonMsg); },
			beforeSendMessageToSession: function(jsonMsg) { return this.beforeSendMessage(jsonMsg); },

			/**
			 * Convenience method to which, by default, all message sending methods call through.
			 * @param msg
			 * @return
			 */
			beforeSendMessage: function(jsonMsg) { return true; },

			/**
			 * Called before an actor joins an activity.
			 * Returning false aborts the attempted join.
			 */
			beforeActivityJoin: function() { return true;},
			
			afterActivityJoin: function() {},
			
			/**
			 * Called before an actor joins an activity.
			 * Returning false aborts the attempted join.
			 */
			beforeActivityCreate: function() { return true; },
			
			afterActivityCreate: function() {},

			/**
			 * Returns an integer priority for this Extra.
			 * Lower priority means closer to switchboard;
			 * Higher means closer to actor.
			 */
			getPriority: function() { return 20; }


		});



	/**
	 *  Class: ExtrasDirector
	 * 
	 *  An aggregate of JunctionExtras. Implements the JunctionExtra interface,
	 *  allows Junction to conveniantly interact with all registered extras.
	 */
	this.ExtrasDirector = Class.extend(
		{

			init: function(){
				this.extras = [];					
			},

			/**
			 * Iterator is applied to each Extra.
			 * @param iterator A function that takes two parametars, the item and its index
			 */
			each: function(iterator){
				var len = this.extras.length;
				for(var i = 0; i < len; i++){
					iterator(this.extras[i], i); 
				}
			},
			
			/**
			 * Iterator is applied to each Extra in reverse order.
			 * @param iterator A function that takes two parametars, the item and its index
			 */
			eachInReverse: function(iterator){
				var len = this.extras.length;
				for(var i = len - 1; i > -1; i--){
					iterator(this.extras[i], i); 
				}
			},

			/**
			 * Adds an Extra to the set of executed extras.
			 * @param extra
			 */
			registerExtra: function(extra){
				this.extras.push(extra);
				this.extras.sort(function(a,b){ 
									 var p1 = a.getPriority();
									 var p2 = b.getPriority();
									 if(p1 < p2) return -1;
									 if(p1 > p2) return 1;
									 return 0;});
			},


			/**
			 * Returns true if onMessageReceived should be called in the usual way.
			 * @param header
			 * @param message
			 * @return
			 */
			beforeOnMessageReceived: function(header, message) {
				var cont = true;
				this.eachInReverse(
					function(ex, i){
						if (!ex.beforeOnMessageReceived(header, message)){
							cont = false;
						}
					});
				return cont;
			},


			afterOnMessageReceived: function(header, message) {
				this.eachInReverse(
					function(ex, i){
						ex.afterOnMessageReceived(header, message);
					});
			},


			beforeSendMessageToActor: function(actorID, message) {
				var cont = true;
				this.each(
					function(ex, i){
						if (!ex.beforeSendMessageToActor(actorID, message)){
							cont = false;
						}
					});
				return cont;
			},


			beforeSendMessageToRole: function(role, message) {
				var cont = true;
				this.each(
					function(ex, i){
						if (!ex.beforeSendMessageToRole(role, message)){
							cont = false;
						}
					});
				return cont;
			},

			beforeSendMessageToSession: function(message) {
				var cont = true;
				this.each(
					function(ex, i){
						if (!ex.beforeSendMessageToSession(message)){
							cont = false;
						}
					});
				return cont;
			},

			beforeActivityJoin: function() {
				var cont = true;
				this.each(
					function(ex, i){
						if (!ex.beforeActivityJoin()){
							cont = false;
						}
					});
				return cont;
			},

			afterActivityJoin: function() {
				this.each(
					function(ex, i){
						ex.afterActivityJoin();
					});
			},

			beforeActivityCreate: function() {
				var cont = true;
				this.each(
					function(ex, i){
						if (!ex.beforeActivityCreate()){
							cont = false;
						}
					});
				return cont;
			},

			afterActivityCreate: function() {
				this.each(
					function(ex, i){
						ex.afterActivityCreate();
					});
			},

			updateInvitationParameters: function(params) {
				this.each(
					function(ex, i){
						ex.updateInvitationParameters(params);
					});
			}


		});


};





/*  TODO - make this stuff work

 // must use a callback since javascript is asynchronous
 , activityDescriptionCallback: function(uri, cb) {
 var parsed = parseUri(uri);
 var switchboard = parsed.host;
 var sessionID = parsed.path.substring(1);

 var _room = sessionID;
 var _component = 'conference.'+switchboard;

 var _jid='junction';
 var _pw='junction';
 var connection = new Strophe.Connection('http://' + switchboard + '/http-bind');
 //connection.rawOutput = function(data) { $('#raw').append('<br/><br/>OUT: '+data.replace(/</g,'&lt;').replace(/>/g,'&gt;')); }
 //connection.rawInput = function(data) { $('#raw').append('<br/><br/>IN: '+data.replace(/</g,'&lt;').replace(/>/g,'&gt;')); }
 var getInfo = function(a) {
 var fields = a.getElementsByTagName('field');
 for (i=0;i<fields.length;i++) {
 if (fields[i].getAttribute('var') == 'muc#roominfo_description') {
 var desc = fields[i].childNodes[0].childNodes[0].wholeText; // get text of value
 var json = JSON.parse(desc);
 cb(json);
 connection.disconnect();
 return false;
 }
 }

 return true;
 };

 connection.connect(_jid,_pw, function(status){
 if (status == Strophe.Status.CONNECTED) {
 // get room info for sessionID
 connection.send(
 $iq({to: _room + "@" + _component, type: 'get'})
 .c("query", {xmlns: "http://jabber.org/protocol/disco#info"}).tree());


 connection.addHandler(getInfo,
 'http://jabber.org/protocol/disco#info',
 null,
 null,null,null);



 }
 });
 }


 , inviteServiceForRole: function(uri, ad, role) {
 if (role == '' || !ad.roles || !ad.roles[role]) return;
 var rolespec = ad.roles[role];
 if (!rolespec) return false;

 actor = {
 mRequest: plat,
 onActivityJoin:
 function() {
 var invite = {action:"cast",activity: uri};
 invite.spec = rolespec;
 invite.role = role;

 this.sendMessageToSession(invite);
 var scopedActor=this;
 var f = function() {
 scopedActor.leave();
 }
 setTimeout(f,500);
 }
 };
 var remoteURI = 'junction://';
 if (plat.switchboard) remoteURI += plat.switchboard;
 else remoteURI += parseUri(uri).host;
 remoteURI += '/jxservice';
 JX.getInstance().newJunction(remoteURI, actor);
 }
 // TODO: Just pass directorURI and activityURI
 // send: {action:cast,activity:uri}
 // have the rest looked up by the other director
 , castActor: function(directorURI, activityURI) {
 this.activityDescriptionCallback(activityURI,
 function(ad){
 if (role == '' || !ad.roles || !ad.roles[role]) return false;
 var rolespec = ad.roles[role];
 actor = {
 onActivityJoin:
 function() {
 var invite = {action:"cast",activity:activityURI};
 this.sendMessageToSession(invite);
 var scopedActor=this;
 var f = function() {
 scopedActor.leave();
 }
 setTimeout(f,500);
 }
 };

 JX.getInstance().newJunction(directorURI,actor);
 });
 }
 };
 }
 }
 }();


 */


// TODO: Use JQuery to load this script from another file

/* randomUUID.js - Version 1.0
 *
 * Copyright 2008, Robert Kieffer
 *
 * This software is made available under the terms of the Open Software License
 * v3.0 (available here: http://www.opensource.org/licenses/osl-3.0.php )
 *
 * The latest version of this file can be found at:
 * http://www.broofa.com/Tools/randomUUID.js
 *
 * For more information, or to comment on this, please go to:
 * http://www.broofa.com/blog/?p=151
 */

/**
 * Create and return a "version 4" RFC-4122 UUID string.
 */

function randomUUID() {
	var s = [], itoh = '0123456789ABCDEF';
	// Make array of random hex digits. The UUID only has 32 digits in it, but we
	// allocate an extra items to make room for the '-'s we'll be inserting.
	for (var i = 0; i <36; i++) s[i] = Math.floor(Math.random()*0x10);

	// Conform to RFC-4122, section 4.4
	s[14] = 4;  // Set 4 high bits of time_high field to version
	s[19] = (s[19] & 0x3) | 0x8;  // Specify 2 high bits of clock sequence

	// Convert to hex chars
	for (var i = 0; i <36; i++) s[i] = itoh[s[i]];

	// Insert '-'s
	s[8] = s[13] = s[18] = s[23] = '-';

	return s.join('');
}



// parseUri 1.2.2
// (c) Steven Levithan <stevenlevithan.com>
// MIT License

function parseUri (str) {
	var	o   = parseUri.options,
	m   = o.parser[o.strictMode ? "strict" : "loose"].exec(str),
	uri = {},
	i   = 14;

	while (i--) uri[o.key[i]] = m[i] || "";

	uri[o.q.name] = {};
	uri[o.key[12]].replace(o.q.parser, function ($0, $1, $2) {
							   if ($1) uri[o.q.name][$1] = $2;
						   });

	return uri;
};

parseUri.options = {
	strictMode: false,
	key: ["source","protocol","authority","userInfo","user","password","host","port","relative","path","directory","file","query","anchor"],
	q:   {
		name:   "queryKey",
		parser: /(?:^|&)([^&=]*)=?([^&]*)/g
	},
	parser: {
		strict: /^(?:([^:\/?#]+):)?(?:\/\/((?:(([^:@]*)(?::([^:@]*))?)?@)?([^:\/?#]*)(?::(\d*))?))?((((?:[^?#\/]*\/)*)([^?#]*))(?:\?([^#]*))?(?:#(.*))?)/,
		loose:  /^(?:(?![^:@]+:[^:@\/]*@)([^:\/?#.]+):)?(?:\/\/)?((?:(([^:@]*)(?::([^:@]*))?)?@)?([^:\/?#]*)(?::(\d*))?)(((\/(?:[^?#](?![^?#\/]*\.[^?#\/.]+(?:[?#]|$)))*\/?)?([^?#\/]*))(?:\?([^#]*))?(?:#(.*))?)/
	}
};



function arrayEquals(a1, a2){
	if(a1.length != a2.length)	return false;
	for(var i = 0; i < a1.length; i++){
		var i1 = a1[i];
		var i2 = a2[i];
		if(!i1.equals || !i2.equals || !i1.equals(i2)) return false;
	}
	return true;
}


function clearArray(a){
	a.splice(0, a.length);
}

function deepObjCopy (dupeObj) {
	var retObj = new Object();
	if (typeof(dupeObj) == 'object') {
		if (typeof(dupeObj.length) != 'undefined'){
			retObj = new Array();
		}
		for (var objInd in dupeObj) {	
			if (typeof(dupeObj[objInd]) == 'object') {
				retObj[objInd] = deepObjCopy(dupeObj[objInd]);
			} else if (typeof(dupeObj[objInd]) == 'string') {
				retObj[objInd] = dupeObj[objInd];
			} else if (typeof(dupeObj[objInd]) == 'number') {
				retObj[objInd] = dupeObj[objInd];
			} else if (typeof(dupeObj[objInd]) == 'boolean') {
				((dupeObj[objInd] == true) ? retObj[objInd] = true : retObj[objInd] = false);
			}
		}
	}
	return retObj;
}


// Implement classical inheritance
//   by John Resig
//   http://ejohn.org/blog/simple-javascript-inheritance/
// 
// Inspired by base2 and Prototype
(function(){
	 var initializing = false, fnTest = /xyz/.test(function(){xyz;}) ? /\b_super\b/ : /.*/;

	 // The base Class implementation (does nothing)
	 this.Class = function(){};
	 
	 // Create a new Class that inherits from this class
	 Class.extend = function(prop) {
		 var _super = this.prototype;
		 
		 // Instantiate a base class (but only create the instance,
		 // don't run the init constructor)
		 initializing = true;
		 var prototype = new this();
		 initializing = false;
		 
		 // Copy the properties over onto the new prototype
		 for (var name in prop) {
			 // Check if we're overwriting an existing function
			 prototype[name] = typeof prop[name] == "function" &&
				 typeof _super[name] == "function" && fnTest.test(prop[name]) ?
				 (function(name, fn){
					  return function() {
						  var tmp = this._super;
						  
						  // Add a new ._super() method that is the same method
						  // but on the super-class
						  this._super = _super[name];
						  
						  // The method only need to be bound temporarily, so we
						  // remove it when we're done executing
						  var ret = fn.apply(this, arguments);       
						  this._super = tmp;
						  
						  return ret;
					  };
				  })(name, prop[name]) :
			 prop[name];
		 }
		 
		 // The dummy class constructor
		 function Class() {
			 // All construction is actually done in the init method
			 if ( !initializing && this.init )
				 this.init.apply(this, arguments);
		 }
		 
		 // Populate our constructed prototype object
		 Class.prototype = prototype;
		 
		 // Enforce the constructor to be what we expect
		 Class.constructor = Class;

		 // And make this class extendable
		 Class.extend = arguments.callee;
		 
		 return Class;
	 };
 })();


//---------------------------------------------------------------------
// QRCode for JavaScript
//
// Copyright (c) 2009 Kazuhiko Arase
//
// URL: http://www.d-project.com/
//
// Licensed under the MIT license:
//   http://www.opensource.org/licenses/mit-license.php
//
// The word "QR Code" is registered trademark of 
// DENSO WAVE INCORPORATED
//   http://www.denso-wave.com/qrcode/faqpatent-e.html
//
//---------------------------------------------------------------------

//---------------------------------------------------------------------
// QR8bitByte
//---------------------------------------------------------------------

function QR8bitByte(data) {
	this.mode = QRMode.MODE_8BIT_BYTE;
	this.data = data;
}

QR8bitByte.prototype = {

	getLength : function(buffer) {
		return this.data.length;
	},
	
	write : function(buffer) {
		for (var i = 0; i < this.data.length; i++) {
			// not JIS ...
			buffer.put(this.data.charCodeAt(i), 8);
		}
	}
};

//---------------------------------------------------------------------
// QRCode
//---------------------------------------------------------------------

function QRCode(typeNumber, errorCorrectLevel) {
	this.typeNumber = typeNumber;
	this.errorCorrectLevel = errorCorrectLevel;
	this.modules = null;
	this.moduleCount = 0;
	this.dataCache = null;
	this.dataList = new Array();
}

QRCode.prototype = {
	
	addData : function(data) {
		var newData = new QR8bitByte(data);
		this.dataList.push(newData);
		this.dataCache = null;
	},
	
	isDark : function(row, col) {
		if (row < 0 || this.moduleCount <= row || col < 0 || this.moduleCount <= col) {
			throw new Error(row + "," + col);
		}
		return this.modules[row][col];
	},

	getModuleCount : function() {
		return this.moduleCount;
	},
	
	make : function() {
		this.makeImpl(false, this.getBestMaskPattern() );
	},
	
	makeImpl : function(test, maskPattern) {
		
		this.moduleCount = this.typeNumber * 4 + 17;
		this.modules = new Array(this.moduleCount);
		
		for (var row = 0; row < this.moduleCount; row++) {
			
			this.modules[row] = new Array(this.moduleCount);
			
			for (var col = 0; col < this.moduleCount; col++) {
				this.modules[row][col] = null;//(col + row) % 3;
			}
		}
	
		this.setupPositionProbePattern(0, 0);
		this.setupPositionProbePattern(this.moduleCount - 7, 0);
		this.setupPositionProbePattern(0, this.moduleCount - 7);
		this.setupPositionAdjustPattern();
		this.setupTimingPattern();
		this.setupTypeInfo(test, maskPattern);
		
		if (this.typeNumber >= 7) {
			this.setupTypeNumber(test);
		}
	
		if (this.dataCache == null) {
			this.dataCache = QRCode.createData(this.typeNumber, this.errorCorrectLevel, this.dataList);
		}
	
		this.mapData(this.dataCache, maskPattern);
	},

	setupPositionProbePattern : function(row, col)  {
		
		for (var r = -1; r <= 7; r++) {
			
			if (row + r <= -1 || this.moduleCount <= row + r) continue;
			
			for (var c = -1; c <= 7; c++) {
				
				if (col + c <= -1 || this.moduleCount <= col + c) continue;
				
				if ( (0 <= r && r <= 6 && (c == 0 || c == 6) )
						|| (0 <= c && c <= 6 && (r == 0 || r == 6) )
						|| (2 <= r && r <= 4 && 2 <= c && c <= 4) ) {
					this.modules[row + r][col + c] = true;
				} else {
					this.modules[row + r][col + c] = false;
				}
			}		
		}		
	},
	
	getBestMaskPattern : function() {
	
		var minLostPoint = 0;
		var pattern = 0;
	
		for (var i = 0; i < 8; i++) {
			
			this.makeImpl(true, i);
	
			var lostPoint = QRUtil.getLostPoint(this);
	
			if (i == 0 || minLostPoint >  lostPoint) {
				minLostPoint = lostPoint;
				pattern = i;
			}
		}
	
		return pattern;
	},
	
	createMovieClip : function(target_mc, instance_name, depth) {
	
		var qr_mc = target_mc.createEmptyMovieClip(instance_name, depth);
		var cs = 1;
	
		this.make();

		for (var row = 0; row < this.modules.length; row++) {
			
			var y = row * cs;
			
			for (var col = 0; col < this.modules[row].length; col++) {
	
				var x = col * cs;
				var dark = this.modules[row][col];
			
				if (dark) {
					qr_mc.beginFill(0, 100);
					qr_mc.moveTo(x, y);
					qr_mc.lineTo(x + cs, y);
					qr_mc.lineTo(x + cs, y + cs);
					qr_mc.lineTo(x, y + cs);
					qr_mc.endFill();
				}
			}
		}
		
		return qr_mc;
	},

	setupTimingPattern : function() {
		
		for (var r = 8; r < this.moduleCount - 8; r++) {
			if (this.modules[r][6] != null) {
				continue;
			}
			this.modules[r][6] = (r % 2 == 0);
		}
	
		for (var c = 8; c < this.moduleCount - 8; c++) {
			if (this.modules[6][c] != null) {
				continue;
			}
			this.modules[6][c] = (c % 2 == 0);
		}
	},
	
	setupPositionAdjustPattern : function() {
	
		var pos = QRUtil.getPatternPosition(this.typeNumber);
		
		for (var i = 0; i < pos.length; i++) {
		
			for (var j = 0; j < pos.length; j++) {
			
				var row = pos[i];
				var col = pos[j];
				
				if (this.modules[row][col] != null) {
					continue;
				}
				
				for (var r = -2; r <= 2; r++) {
				
					for (var c = -2; c <= 2; c++) {
					
						if (r == -2 || r == 2 || c == -2 || c == 2 
								|| (r == 0 && c == 0) ) {
							this.modules[row + r][col + c] = true;
						} else {
							this.modules[row + r][col + c] = false;
						}
					}
				}
			}
		}
	},
	
	setupTypeNumber : function(test) {
	
		var bits = QRUtil.getBCHTypeNumber(this.typeNumber);
	
		for (var i = 0; i < 18; i++) {
			var mod = (!test && ( (bits >> i) & 1) == 1);
			this.modules[Math.floor(i / 3)][i % 3 + this.moduleCount - 8 - 3] = mod;
		}
	
		for (var i = 0; i < 18; i++) {
			var mod = (!test && ( (bits >> i) & 1) == 1);
			this.modules[i % 3 + this.moduleCount - 8 - 3][Math.floor(i / 3)] = mod;
		}
	},
	
	setupTypeInfo : function(test, maskPattern) {
	
		var data = (this.errorCorrectLevel << 3) | maskPattern;
		var bits = QRUtil.getBCHTypeInfo(data);
	
		// vertical		
		for (var i = 0; i < 15; i++) {
	
			var mod = (!test && ( (bits >> i) & 1) == 1);
	
			if (i < 6) {
				this.modules[i][8] = mod;
			} else if (i < 8) {
				this.modules[i + 1][8] = mod;
			} else {
				this.modules[this.moduleCount - 15 + i][8] = mod;
			}
		}
	
		// horizontal
		for (var i = 0; i < 15; i++) {
	
			var mod = (!test && ( (bits >> i) & 1) == 1);
			
			if (i < 8) {
				this.modules[8][this.moduleCount - i - 1] = mod;
			} else if (i < 9) {
				this.modules[8][15 - i - 1 + 1] = mod;
			} else {
				this.modules[8][15 - i - 1] = mod;
			}
		}
	
		// fixed module
		this.modules[this.moduleCount - 8][8] = (!test);
	
	},
	
	mapData : function(data, maskPattern) {
		
		var inc = -1;
		var row = this.moduleCount - 1;
		var bitIndex = 7;
		var byteIndex = 0;
		
		for (var col = this.moduleCount - 1; col > 0; col -= 2) {
	
			if (col == 6) col--;
	
			while (true) {
	
				for (var c = 0; c < 2; c++) {
					
					if (this.modules[row][col - c] == null) {
						
						var dark = false;
	
						if (byteIndex < data.length) {
							dark = ( ( (data[byteIndex] >>> bitIndex) & 1) == 1);
						}
	
						var mask = QRUtil.getMask(maskPattern, row, col - c);
	
						if (mask) {
							dark = !dark;
						}
						
						this.modules[row][col - c] = dark;
						bitIndex--;
	
						if (bitIndex == -1) {
							byteIndex++;
							bitIndex = 7;
						}
					}
				}
								
				row += inc;
	
				if (row < 0 || this.moduleCount <= row) {
					row -= inc;
					inc = -inc;
					break;
				}
			}
		}
		
	}

};

QRCode.PAD0 = 0xEC;
QRCode.PAD1 = 0x11;

QRCode.createData = function(typeNumber, errorCorrectLevel, dataList) {
	
	var rsBlocks = QRRSBlock.getRSBlocks(typeNumber, errorCorrectLevel);
	
	var buffer = new QRBitBuffer();
	
	for (var i = 0; i < dataList.length; i++) {
		var data = dataList[i];
		buffer.put(data.mode, 4);
		buffer.put(data.getLength(), QRUtil.getLengthInBits(data.mode, typeNumber) );
		data.write(buffer);
	}

	// calc num max data.
	var totalDataCount = 0;
	for (var i = 0; i < rsBlocks.length; i++) {
		totalDataCount += rsBlocks[i].dataCount;
	}

	if (buffer.getLengthInBits() > totalDataCount * 8) {
		throw new Error("code length overflow. ("
			+ buffer.getLengthInBits()
			+ ">"
			+  totalDataCount * 8
			+ ")");
	}

	// end code
	if (buffer.getLengthInBits() + 4 <= totalDataCount * 8) {
		buffer.put(0, 4);
	}

	// padding
	while (buffer.getLengthInBits() % 8 != 0) {
		buffer.putBit(false);
	}

	// padding
	while (true) {
		
		if (buffer.getLengthInBits() >= totalDataCount * 8) {
			break;
		}
		buffer.put(QRCode.PAD0, 8);
		
		if (buffer.getLengthInBits() >= totalDataCount * 8) {
			break;
		}
		buffer.put(QRCode.PAD1, 8);
	}

	return QRCode.createBytes(buffer, rsBlocks);
}

QRCode.createBytes = function(buffer, rsBlocks) {

	var offset = 0;
	
	var maxDcCount = 0;
	var maxEcCount = 0;
	
	var dcdata = new Array(rsBlocks.length);
	var ecdata = new Array(rsBlocks.length);
	
	for (var r = 0; r < rsBlocks.length; r++) {

		var dcCount = rsBlocks[r].dataCount;
		var ecCount = rsBlocks[r].totalCount - dcCount;

		maxDcCount = Math.max(maxDcCount, dcCount);
		maxEcCount = Math.max(maxEcCount, ecCount);
		
		dcdata[r] = new Array(dcCount);
		
		for (var i = 0; i < dcdata[r].length; i++) {
			dcdata[r][i] = 0xff & buffer.buffer[i + offset];
		}
		offset += dcCount;
		
		var rsPoly = QRUtil.getErrorCorrectPolynomial(ecCount);
		var rawPoly = new QRPolynomial(dcdata[r], rsPoly.getLength() - 1);

		var modPoly = rawPoly.mod(rsPoly);
		ecdata[r] = new Array(rsPoly.getLength() - 1);
		for (var i = 0; i < ecdata[r].length; i++) {
            var modIndex = i + modPoly.getLength() - ecdata[r].length;
			ecdata[r][i] = (modIndex >= 0)? modPoly.get(modIndex) : 0;
		}

	}
	
	var totalCodeCount = 0;
	for (var i = 0; i < rsBlocks.length; i++) {
		totalCodeCount += rsBlocks[i].totalCount;
	}

	var data = new Array(totalCodeCount);
	var index = 0;

	for (var i = 0; i < maxDcCount; i++) {
		for (var r = 0; r < rsBlocks.length; r++) {
			if (i < dcdata[r].length) {
				data[index++] = dcdata[r][i];
			}
		}
	}

	for (var i = 0; i < maxEcCount; i++) {
		for (var r = 0; r < rsBlocks.length; r++) {
			if (i < ecdata[r].length) {
				data[index++] = ecdata[r][i];
			}
		}
	}

	return data;

}

//---------------------------------------------------------------------
// QRMode
//---------------------------------------------------------------------

var QRMode = {
	MODE_NUMBER :		1 << 0,
	MODE_ALPHA_NUM : 	1 << 1,
	MODE_8BIT_BYTE : 	1 << 2,
	MODE_KANJI :		1 << 3
};

//---------------------------------------------------------------------
// QRErrorCorrectLevel
//---------------------------------------------------------------------
 
var QRErrorCorrectLevel = {
	L : 1,
	M : 0,
	Q : 3,
	H : 2
};

//---------------------------------------------------------------------
// QRMaskPattern
//---------------------------------------------------------------------

var QRMaskPattern = {
	PATTERN000 : 0,
	PATTERN001 : 1,
	PATTERN010 : 2,
	PATTERN011 : 3,
	PATTERN100 : 4,
	PATTERN101 : 5,
	PATTERN110 : 6,
	PATTERN111 : 7
};

//---------------------------------------------------------------------
// QRUtil
//---------------------------------------------------------------------
 
var QRUtil = {

    PATTERN_POSITION_TABLE : [
	    [],
	    [6, 18],
	    [6, 22],
	    [6, 26],
	    [6, 30],
	    [6, 34],
	    [6, 22, 38],
	    [6, 24, 42],
	    [6, 26, 46],
	    [6, 28, 50],
	    [6, 30, 54],		
	    [6, 32, 58],
	    [6, 34, 62],
	    [6, 26, 46, 66],
	    [6, 26, 48, 70],
	    [6, 26, 50, 74],
	    [6, 30, 54, 78],
	    [6, 30, 56, 82],
	    [6, 30, 58, 86],
	    [6, 34, 62, 90],
	    [6, 28, 50, 72, 94],
	    [6, 26, 50, 74, 98],
	    [6, 30, 54, 78, 102],
	    [6, 28, 54, 80, 106],
	    [6, 32, 58, 84, 110],
	    [6, 30, 58, 86, 114],
	    [6, 34, 62, 90, 118],
	    [6, 26, 50, 74, 98, 122],
	    [6, 30, 54, 78, 102, 126],
	    [6, 26, 52, 78, 104, 130],
	    [6, 30, 56, 82, 108, 134],
	    [6, 34, 60, 86, 112, 138],
	    [6, 30, 58, 86, 114, 142],
	    [6, 34, 62, 90, 118, 146],
	    [6, 30, 54, 78, 102, 126, 150],
	    [6, 24, 50, 76, 102, 128, 154],
	    [6, 28, 54, 80, 106, 132, 158],
	    [6, 32, 58, 84, 110, 136, 162],
	    [6, 26, 54, 82, 110, 138, 166],
	    [6, 30, 58, 86, 114, 142, 170]
    ],

    G15 : (1 << 10) | (1 << 8) | (1 << 5) | (1 << 4) | (1 << 2) | (1 << 1) | (1 << 0),
    G18 : (1 << 12) | (1 << 11) | (1 << 10) | (1 << 9) | (1 << 8) | (1 << 5) | (1 << 2) | (1 << 0),
    G15_MASK : (1 << 14) | (1 << 12) | (1 << 10)	| (1 << 4) | (1 << 1),

    getBCHTypeInfo : function(data) {
	    var d = data << 10;
	    while (QRUtil.getBCHDigit(d) - QRUtil.getBCHDigit(QRUtil.G15) >= 0) {
		    d ^= (QRUtil.G15 << (QRUtil.getBCHDigit(d) - QRUtil.getBCHDigit(QRUtil.G15) ) ); 	
	    }
	    return ( (data << 10) | d) ^ QRUtil.G15_MASK;
    },

    getBCHTypeNumber : function(data) {
	    var d = data << 12;
	    while (QRUtil.getBCHDigit(d) - QRUtil.getBCHDigit(QRUtil.G18) >= 0) {
		    d ^= (QRUtil.G18 << (QRUtil.getBCHDigit(d) - QRUtil.getBCHDigit(QRUtil.G18) ) ); 	
	    }
	    return (data << 12) | d;
    },

    getBCHDigit : function(data) {

	    var digit = 0;

	    while (data != 0) {
		    digit++;
		    data >>>= 1;
	    }

	    return digit;
    },

    getPatternPosition : function(typeNumber) {
	    return QRUtil.PATTERN_POSITION_TABLE[typeNumber - 1];
    },

    getMask : function(maskPattern, i, j) {
	    
	    switch (maskPattern) {
		    
	    case QRMaskPattern.PATTERN000 : return (i + j) % 2 == 0;
	    case QRMaskPattern.PATTERN001 : return i % 2 == 0;
	    case QRMaskPattern.PATTERN010 : return j % 3 == 0;
	    case QRMaskPattern.PATTERN011 : return (i + j) % 3 == 0;
	    case QRMaskPattern.PATTERN100 : return (Math.floor(i / 2) + Math.floor(j / 3) ) % 2 == 0;
	    case QRMaskPattern.PATTERN101 : return (i * j) % 2 + (i * j) % 3 == 0;
	    case QRMaskPattern.PATTERN110 : return ( (i * j) % 2 + (i * j) % 3) % 2 == 0;
	    case QRMaskPattern.PATTERN111 : return ( (i * j) % 3 + (i + j) % 2) % 2 == 0;

	    default :
		    throw new Error("bad maskPattern:" + maskPattern);
	    }
    },

    getErrorCorrectPolynomial : function(errorCorrectLength) {

	    var a = new QRPolynomial([1], 0);

	    for (var i = 0; i < errorCorrectLength; i++) {
		    a = a.multiply(new QRPolynomial([1, QRMath.gexp(i)], 0) );
	    }

	    return a;
    },

    getLengthInBits : function(mode, type) {

	    if (1 <= type && type < 10) {

		    // 1 - 9

		    switch(mode) {
		    case QRMode.MODE_NUMBER 	: return 10;
		    case QRMode.MODE_ALPHA_NUM 	: return 9;
		    case QRMode.MODE_8BIT_BYTE	: return 8;
		    case QRMode.MODE_KANJI  	: return 8;
		    default :
			    throw new Error("mode:" + mode);
		    }

	    } else if (type < 27) {

		    // 10 - 26

		    switch(mode) {
		    case QRMode.MODE_NUMBER 	: return 12;
		    case QRMode.MODE_ALPHA_NUM 	: return 11;
		    case QRMode.MODE_8BIT_BYTE	: return 16;
		    case QRMode.MODE_KANJI  	: return 10;
		    default :
			    throw new Error("mode:" + mode);
		    }

	    } else if (type < 41) {

		    // 27 - 40

		    switch(mode) {
		    case QRMode.MODE_NUMBER 	: return 14;
		    case QRMode.MODE_ALPHA_NUM	: return 13;
		    case QRMode.MODE_8BIT_BYTE	: return 16;
		    case QRMode.MODE_KANJI  	: return 12;
		    default :
			    throw new Error("mode:" + mode);
		    }

	    } else {
		    throw new Error("type:" + type);
	    }
    },

    getLostPoint : function(qrCode) {
	    
	    var moduleCount = qrCode.getModuleCount();
	    
	    var lostPoint = 0;
	    
	    // LEVEL1
	    
	    for (var row = 0; row < moduleCount; row++) {

		    for (var col = 0; col < moduleCount; col++) {

			    var sameCount = 0;
			    var dark = qrCode.isDark(row, col);

				for (var r = -1; r <= 1; r++) {

				    if (row + r < 0 || moduleCount <= row + r) {
					    continue;
				    }

				    for (var c = -1; c <= 1; c++) {

					    if (col + c < 0 || moduleCount <= col + c) {
						    continue;
					    }

					    if (r == 0 && c == 0) {
						    continue;
					    }

					    if (dark == qrCode.isDark(row + r, col + c) ) {
						    sameCount++;
					    }
				    }
			    }

			    if (sameCount > 5) {
				    lostPoint += (3 + sameCount - 5);
			    }
		    }
	    }

	    // LEVEL2

	    for (var row = 0; row < moduleCount - 1; row++) {
		    for (var col = 0; col < moduleCount - 1; col++) {
			    var count = 0;
			    if (qrCode.isDark(row,     col    ) ) count++;
			    if (qrCode.isDark(row + 1, col    ) ) count++;
			    if (qrCode.isDark(row,     col + 1) ) count++;
			    if (qrCode.isDark(row + 1, col + 1) ) count++;
			    if (count == 0 || count == 4) {
				    lostPoint += 3;
			    }
		    }
	    }

	    // LEVEL3

	    for (var row = 0; row < moduleCount; row++) {
		    for (var col = 0; col < moduleCount - 6; col++) {
			    if (qrCode.isDark(row, col)
					    && !qrCode.isDark(row, col + 1)
					    &&  qrCode.isDark(row, col + 2)
					    &&  qrCode.isDark(row, col + 3)
					    &&  qrCode.isDark(row, col + 4)
					    && !qrCode.isDark(row, col + 5)
					    &&  qrCode.isDark(row, col + 6) ) {
				    lostPoint += 40;
			    }
		    }
	    }

	    for (var col = 0; col < moduleCount; col++) {
		    for (var row = 0; row < moduleCount - 6; row++) {
			    if (qrCode.isDark(row, col)
					    && !qrCode.isDark(row + 1, col)
					    &&  qrCode.isDark(row + 2, col)
					    &&  qrCode.isDark(row + 3, col)
					    &&  qrCode.isDark(row + 4, col)
					    && !qrCode.isDark(row + 5, col)
					    &&  qrCode.isDark(row + 6, col) ) {
				    lostPoint += 40;
			    }
		    }
	    }

	    // LEVEL4
	    
	    var darkCount = 0;

	    for (var col = 0; col < moduleCount; col++) {
		    for (var row = 0; row < moduleCount; row++) {
			    if (qrCode.isDark(row, col) ) {
				    darkCount++;
			    }
		    }
	    }
	    
	    var ratio = Math.abs(100 * darkCount / moduleCount / moduleCount - 50) / 5;
	    lostPoint += ratio * 10;

	    return lostPoint;		
    }

};


//---------------------------------------------------------------------
// QRMath
//---------------------------------------------------------------------

var QRMath = {

	glog : function(n) {
	
		if (n < 1) {
			throw new Error("glog(" + n + ")");
		}
		
		return QRMath.LOG_TABLE[n];
	},
	
	gexp : function(n) {
	
		while (n < 0) {
			n += 255;
		}
	
		while (n >= 256) {
			n -= 255;
		}
	
		return QRMath.EXP_TABLE[n];
	},
	
	EXP_TABLE : new Array(256),
	
	LOG_TABLE : new Array(256)

};
	
for (var i = 0; i < 8; i++) {
	QRMath.EXP_TABLE[i] = 1 << i;
}
for (var i = 8; i < 256; i++) {
	QRMath.EXP_TABLE[i] = QRMath.EXP_TABLE[i - 4]
		^ QRMath.EXP_TABLE[i - 5]
		^ QRMath.EXP_TABLE[i - 6]
		^ QRMath.EXP_TABLE[i - 8];
}
for (var i = 0; i < 255; i++) {
	QRMath.LOG_TABLE[QRMath.EXP_TABLE[i] ] = i;
}

//---------------------------------------------------------------------
// QRPolynomial
//---------------------------------------------------------------------

function QRPolynomial(num, shift) {

	if (num.length == undefined) {
		throw new Error(num.length + "/" + shift);
	}

	var offset = 0;

	while (offset < num.length && num[offset] == 0) {
		offset++;
	}

	this.num = new Array(num.length - offset + shift);
	for (var i = 0; i < num.length - offset; i++) {
		this.num[i] = num[i + offset];
	}
}

QRPolynomial.prototype = {

	get : function(index) {
		return this.num[index];
	},
	
	getLength : function() {
		return this.num.length;
	},
	
	multiply : function(e) {
	
		var num = new Array(this.getLength() + e.getLength() - 1);
	
		for (var i = 0; i < this.getLength(); i++) {
			for (var j = 0; j < e.getLength(); j++) {
				num[i + j] ^= QRMath.gexp(QRMath.glog(this.get(i) ) + QRMath.glog(e.get(j) ) );
			}
		}
	
		return new QRPolynomial(num, 0);
	},
	
	mod : function(e) {
	
		if (this.getLength() - e.getLength() < 0) {
			return this;
		}
	
		var ratio = QRMath.glog(this.get(0) ) - QRMath.glog(e.get(0) );
	
		var num = new Array(this.getLength() );
		
		for (var i = 0; i < this.getLength(); i++) {
			num[i] = this.get(i);
		}
		
		for (var i = 0; i < e.getLength(); i++) {
			num[i] ^= QRMath.gexp(QRMath.glog(e.get(i) ) + ratio);
		}
	
		// recursive call
		return new QRPolynomial(num, 0).mod(e);
	}
};

//---------------------------------------------------------------------
// QRRSBlock
//---------------------------------------------------------------------

function QRRSBlock(totalCount, dataCount) {
	this.totalCount = totalCount;
	this.dataCount  = dataCount;
}

QRRSBlock.RS_BLOCK_TABLE = [

	// L
	// M
	// Q
	// H

	// 1
	[1, 26, 19],
	[1, 26, 16],
	[1, 26, 13],
	[1, 26, 9],
	
	// 2
	[1, 44, 34],
	[1, 44, 28],
	[1, 44, 22],
	[1, 44, 16],

	// 3
	[1, 70, 55],
	[1, 70, 44],
	[2, 35, 17],
	[2, 35, 13],

	// 4		
	[1, 100, 80],
	[2, 50, 32],
	[2, 50, 24],
	[4, 25, 9],
	
	// 5
	[1, 134, 108],
	[2, 67, 43],
	[2, 33, 15, 2, 34, 16],
	[2, 33, 11, 2, 34, 12],
	
	// 6
	[2, 86, 68],
	[4, 43, 27],
	[4, 43, 19],
	[4, 43, 15],
	
	// 7		
	[2, 98, 78],
	[4, 49, 31],
	[2, 32, 14, 4, 33, 15],
	[4, 39, 13, 1, 40, 14],
	
	// 8
	[2, 121, 97],
	[2, 60, 38, 2, 61, 39],
	[4, 40, 18, 2, 41, 19],
	[4, 40, 14, 2, 41, 15],
	
	// 9
	[2, 146, 116],
	[3, 58, 36, 2, 59, 37],
	[4, 36, 16, 4, 37, 17],
	[4, 36, 12, 4, 37, 13],
	
	// 10		
	[2, 86, 68, 2, 87, 69],
	[4, 69, 43, 1, 70, 44],
	[6, 43, 19, 2, 44, 20],
	[6, 43, 15, 2, 44, 16]

];

QRRSBlock.getRSBlocks = function(typeNumber, errorCorrectLevel) {
	
	var rsBlock = QRRSBlock.getRsBlockTable(typeNumber, errorCorrectLevel);
	
	if (rsBlock == undefined) {
		throw new Error("bad rs block @ typeNumber:" + typeNumber + "/errorCorrectLevel:" + errorCorrectLevel);
	}

	var length = rsBlock.length / 3;
	
	var list = new Array();
	
	for (var i = 0; i < length; i++) {

		var count = rsBlock[i * 3 + 0];
		var totalCount = rsBlock[i * 3 + 1];
		var dataCount  = rsBlock[i * 3 + 2];

		for (var j = 0; j < count; j++) {
			list.push(new QRRSBlock(totalCount, dataCount) );	
		}
	}
	
	return list;
}

QRRSBlock.getRsBlockTable = function(typeNumber, errorCorrectLevel) {

	switch(errorCorrectLevel) {
	case QRErrorCorrectLevel.L :
		return QRRSBlock.RS_BLOCK_TABLE[(typeNumber - 1) * 4 + 0];
	case QRErrorCorrectLevel.M :
		return QRRSBlock.RS_BLOCK_TABLE[(typeNumber - 1) * 4 + 1];
	case QRErrorCorrectLevel.Q :
		return QRRSBlock.RS_BLOCK_TABLE[(typeNumber - 1) * 4 + 2];
	case QRErrorCorrectLevel.H :
		return QRRSBlock.RS_BLOCK_TABLE[(typeNumber - 1) * 4 + 3];
	default :
		return undefined;
	}
}

//---------------------------------------------------------------------
// QRBitBuffer
//---------------------------------------------------------------------

function QRBitBuffer() {
	this.buffer = new Array();
	this.length = 0;
}

QRBitBuffer.prototype = {

	get : function(index) {
		var bufIndex = Math.floor(index / 8);
		return ( (this.buffer[bufIndex] >>> (7 - index % 8) ) & 1) == 1;
	},
	
	put : function(num, length) {
		for (var i = 0; i < length; i++) {
			this.putBit( ( (num >>> (length - i - 1) ) & 1) == 1);
		}
	},
	
	getLengthInBits : function() {
		return this.length;
	},
	
	putBit : function(bit) {
	
		var bufIndex = Math.floor(this.length / 8);
		if (this.buffer.length <= bufIndex) {
			this.buffer.push(0);
		}
	
		if (bit) {
			this.buffer[bufIndex] |= (0x80 >>> (this.length % 8) );
		}
	
		this.length++;
	}
};

// From jquery.qrcode.js
var createQRCanvas = function(options) {
	correctLevel = QRErrorCorrectLevel.L;

	// create the qrcode itself
	// TODO: gross; use math.
	var qrcode;
	var typeNumber = 4;
	while (typeNumber < 41) {
  	  try {
	   	qrcode = new QRCode(typeNumber, correctLevel);
		qrcode.addData(options.text);
		qrcode.make();
		break;
	  } catch (e) {
		typeNumber++;
	  }
	}

	// create canvas element
	var canvas	= document.createElement('canvas');
	canvas.width	= options.width;
	canvas.height	= options.height;
	var ctx		= canvas.getContext('2d');

	// compute tileW/tileH based on options.width/options.height
	var FRAME = 3;
	var tileW	= options.width  / (qrcode.getModuleCount() + 2*FRAME);
	var tileH	= options.height / (qrcode.getModuleCount() + 2*FRAME);
	// draw in the canvas
	ctx.fillStyle = "#fff";
	ctx.fillRect(0, 0, canvas.width, canvas.height);
	ctx.fillStyle = "#000";

	for( var row = 0; row < qrcode.getModuleCount(); row++ ){
		for( var col = 0; col < qrcode.getModuleCount(); col++ ){
			if (qrcode.isDark(row, col)) {
				ctx.fillRect((FRAME + col)*tileW, (FRAME + row)*tileH, tileW + 1, tileH + 1);
			}
		}	
	}
	// return just built canvas
	return canvas;
}


var JX = new _JX();
JX.getInstance = function(config) { return new _JX(config); };
