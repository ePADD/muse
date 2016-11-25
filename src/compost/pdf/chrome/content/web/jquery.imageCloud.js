/*
 * ImageCloud - jQuery plugin 2.0
 *
 * Copyright (c) 2011 Alvaro Montoro Dominguez
 *
 * Licensed under the GPL license:
 *   http://www.gnu.org/licenses/gpl.html
 *
 */
 
;(function ($) {

    $.fn.imageCloud=function (options) {
	    
        var settings={
            'width': 600,
            'height': 400,
            'link': false,
            'color': '#800000',
			'speed': 250,
            'borderSize': 6,
            'borderStyle': 'solid',
            'borderRadius': 0
        };

        return this.each(function () {
        
            function ic_collision(auxX, auxY, sizeX, sizeY, arrFrames, auxSettings) {
                if (auxX/1+sizeX/1+auxSettings.borderSize*2 > auxSettings.width) { return 1; }
                if (auxY/1+sizeY/1+auxSettings.borderSize*2 > auxSettings.height) { return 1; }
                if (auxY < 0 || auxX < 0) { return 1; }

                for (x=0; x < arrFrames.length; x++) {

                    var a1=arrFrames[x][0];
                    var a2=arrFrames[x][1];
                    var b1=auxX;
                    var b2=auxY;
                    var awidth=arrFrames[x][2];
                    var aheight=arrFrames[x][3];
                    var bwidth=sizeX;
                    var bheight=sizeY;

                    if (b1>=a1 && b1<=a1+awidth+15 && b2>=a2 && b2<=a2+aheight+15) { return 1; }
                    if (a1>=b1 && a1<=b1+bwidth+15 && a2>=b2 && a2<=b2+bheight+15) { return 1; }
                    if (b1>=a1 && b1<=a1+awidth+15 && a2>=b2 && a2<=b2+bheight+15) { return 1; }
                    if (a1>=b1 && a1<=b1+bwidth+15 && b2>=a2 && b2<=a2+aheight+15) { return 1; }
                    
                }
                
                return 0;
                
            }
            
            function ic_calculatePosition(arrayFrames, activeFrame, attempt, tryAttempt) {

                if (ic_currentImage == 0) {
                    ic_posX=0;
                    ic_posY=0;
                    ic_bgPosX=0;
                    ic_bgPosY=0;

                    var auxFrame=new Array(4);
                    auxFrame[0]=ic_posX;
                    auxFrame[1]=ic_posY;
                    auxFrame[2]=ic_imageSizes[ic_imageType][0];
                    auxFrame[3]=ic_imageSizes[ic_imageType][1];
                    arrayFrames.push(auxFrame);

                    return 1;

                } else {

                    if (activeFrame > 0) {
                        switch (tryAttempt) {
                            case 0: {
                                ic_posX=Math.floor(Math.random() * (arrayFrames[activeFrame-1][0]/1+arrayFrames[activeFrame-1][2]/1));
                                ic_posY=arrayFrames[activeFrame-1][1]/1+arrayFrames[activeFrame-1][3]/1+20;
                                break;
                            }
                            case 1: {
                                ic_posX=Math.floor(Math.random() * (arrayFrames[activeFrame-1][0]/1+arrayFrames[activeFrame-1][2]/1));
                                ic_posY=arrayFrames[activeFrame-1][1]/1 - arrayFrames[activeFrame-1][3]/1- 20;
                                break;
                            }
                            case 2: {
                                ic_posX=arrayFrames[activeFrame-1][0]/1+arrayFrames[activeFrame-1][2]/1+20;
                                ic_posY=Math.floor(Math.random() * (arrayFrames[activeFrame-1][1]/1+arrayFrames[activeFrame-1][3]/1));
                                break;
                            }
                            case 3: {
                                ic_posX=arrayFrames[activeFrame-1][0]/1 - arrayFrames[activeFrame-1][2]/1 - 20;
                                ic_posY=Math.floor(Math.random() * (arrayFrames[activeFrame-1][1]/1+arrayFrames[activeFrame-1][3]/1));
                                break;
                            }
                        }

                        if (ic_collision(ic_posX, ic_posY, ic_imageSizes[ic_imageType][0], ic_imageSizes[ic_imageType][1], arrayFrames, settings) == 1) { return 0; }
                        
                        ic_bgPosX=(Math.floor(Math.random() * (ic_imageSizes[ic_imageType][0] - ic_arrayImages[ic_currentImage].width)));
                        ic_bgPosY=(Math.floor(Math.random() * (ic_imageSizes[ic_imageType][1] - ic_arrayImages[ic_currentImage].height)));

                        ic_bgPosX=ic_bgPosX - ic_bgPosX % 5;
                        ic_bgPosY=ic_bgPosY - ic_bgPosY % 5;

                        var auxFrame=new Array(4);
                        auxFrame[0]=ic_posX;
                        auxFrame[1]=ic_posY;
                        auxFrame[2]=ic_imageSizes[ic_imageType][0];
                        auxFrame[3]=ic_imageSizes[ic_imageType][1];
                        arrayFrames.push(auxFrame);

                        return 1;
                    }
                }
                
                return 0;
            }

            // extend the default settings
            if ( options ) {
                $.extend( settings, options );
            }
            var $this=$(this);
            var ic_strSizes="";
            var ic_strCloud="";
            var ic_strExit =0;
            var ic_currentImage=0;
            var ic_validPos=0;
            var ic_imageType=0;
            var ic_arrayImages;
            var ic_posX=0;
            var ic_posY=0;
            var ic_arrayFrames=[];
            // these are the default sizes for the frames (width x height)
            var ic_imageSizes =[[50,50], [60,60], [70,70], [80,80], [90,90], [100,100], [120,120], [50,60], [70,50], [80,120], [50,100], [60,150], [150,80], [120,80], [100,60], [150,50], [50,150]];

            // change the target div to the required size
            if ($this.css("position") != "absolute") {
                $this.css("position", "relative");
            }
            $this.height(settings.height);
            $this.width(settings.width);
            $this.css("overflow", "visible");
            $this.attr("class", "imagecloud");

            // get all the images inside the div
            ic_arrayImages=$this.find("img");

            // while there are images, we put them in the cloud
            while (ic_strExit == 0 && ic_currentImage < ic_arrayImages.length) {

                var auxIc_currentImage=ic_currentImage;

                ic_validPos=0;
                ic_imageType=Math.floor(Math.random()*(ic_imageSizes.length));
                
                // BEGINNING OF calculateCoordinates
                while (auxIc_currentImage > -1) {

                    var auxAttempts=0;
                    var auxMaxAttempsPerSector=100;

                    while (auxAttempts < auxMaxAttempsPerSector*4) {
                        ic_validPos=ic_calculatePosition(ic_arrayFrames, auxIc_currentImage, auxAttempts, auxAttempts%4);
                        if (ic_validPos==1) { auxAttempts=1000; auxIc_currentImage=-1000; }
                        auxAttempts++;
                    }

                    auxIc_currentImage--;
                }
                // END OF calculateCoordinates
                
                if (ic_validPos == 1) {
                    ic_strSizes=ic_strSizes+'var ic_i'+ic_currentImage+'w  ='+ic_imageSizes[ic_imageType][0]+'; ' +
                                            'var ic_i'+ic_currentImage+'h  ='+ic_imageSizes[ic_imageType][1]+'; ' +
                                            'var ic_i'+ic_currentImage+'t  ='+ic_posY+'; ' +
                                            'var ic_i'+ic_currentImage+'l  ='+ic_posX+'; ' +
                                            'var ic_i'+ic_currentImage+'bgt='+ic_bgPosY+'; '+
                                            'var ic_i'+ic_currentImage+'bgl='+ic_bgPosX+'; ' +
                                            'var ic_i'+ic_currentImage+'bgw='+ic_arrayImages[ic_currentImage].width+'; ' +
                                            'var ic_i'+ic_currentImage+'bgh='+ic_arrayImages[ic_currentImage].height+';\n';
                                                
                    ic_strCloud=ic_strCloud+'<div id="ic_i'+ic_currentImage+'" class="ci_imagen" ';
                    
                    if (settings.link && ic_arrayImages[ic_currentImage].title) { ic_strCloud=ic_strCloud+' onclick="window.location=\''+ic_arrayImages[ic_currentImage].title+'\'" '; }
                    
                    ic_strCloud=ic_strCloud+' style="overflow:hidden;position:absolute;border-radius:' + settings.borderRadius + 'px;-moz-border-radius:' + settings.borderRadius + 'px;-webkit-border-radius:' + settings.borderRadius + 'px;border:' + settings.borderSize + 'px ' + settings.borderStyle + ' ' + settings.color + ';cursor:pointer;top:'+ic_posY+'px;left:'+ic_posX+'px;width:'+ic_imageSizes[ic_imageType][0]+'px;height:'+ic_imageSizes[ic_imageType][1]+'px;"><img src="'+ic_arrayImages[ic_currentImage].src+'" width="'+ic_arrayImages[ic_currentImage].width+'" height="'+ic_arrayImages[ic_currentImage].height+'" style="position:absolute;left: '+ic_bgPosX+'px;top:'+ic_bgPosY+'px;" /></div>\n';

                    ic_currentImage++;
                } else {
                    ic_strExit=1;
                }
                
            }

            // display the images
            $this.html('<script>function difference(value1, value2) { value1=value1.replace("px", "");value2=value2.replace("px", "");value3=((value1/1+value2/1)+"px");return value3;}\n'+ic_strSizes+"</script>\n"+ic_strCloud);

            $(".ci_imagen").mouseenter(function() {
                       
                        if (!this.ao) {
                            this.ao={id:this.id, ft:eval(this.id+"t"), fl:eval(this.id+"l"), fh:eval(this.id+"h"), fw:eval(this.id+"w"), bt:eval(this.id+"bgt"), bl:eval(this.id+"bgl"), bh:eval(this.id+"bgh"), bw:eval(this.id+"bgw"), st:0};

                        }
                       
                        $(this.ao).stop(true, false).animate({
                            st: 100
                        }, {
                            duration: settings.speed,

                            step: function(now,fx) {
                               
                                var changeT=Math.floor((fx.elem.bt)*(now/100));
                                var changeL=Math.floor((fx.elem.bl)*(now/100));
                                $("#"+fx.elem.id).css({
                                    width: fx.elem.fw+(fx.elem.bw-fx.elem.fw)*(now/100),
                                    height: fx.elem.fh+(fx.elem.bh-fx.elem.fh)*(now/100),
                                    top: fx.elem.ft+changeT,
                                    left: fx.elem.fl+changeL,
                                    zIndex:99999

                                }).find("img").css({
                                    top: fx.elem.bt - changeT,
                                    left: fx.elem.bl - changeL
                                });
                            }
                        }, 'linear');
                       
                    }).mouseleave(function() {
                       
                        $(this.ao).stop(true, false).animate({
                            st: 0
                        }, {
                            duration: settings.speed,

                            step: function(now,fx) {
                               
                                var changeT=Math.floor((fx.elem.bt)*(now/100));
                                var changeL=Math.floor((fx.elem.bl)*(now/100));
                                $("#"+fx.elem.id).css({
                                    width: fx.elem.fw+(fx.elem.bw-fx.elem.fw)*(now/100),
                                    height: fx.elem.fh+(fx.elem.bh-fx.elem.fh)*(now/100),
                                    top: fx.elem.ft+changeT,
                                    left: fx.elem.fl+changeL,
                                    zIndex:9999

                                }).find("img").css({
                                    top: fx.elem.bt - changeT,
                                    left: fx.elem.bl - changeL
                                });
                            }
                        }, 'linear');
                       
                    });
        });

    };
})(jQuery);

$(document).ready(function() { $(".imagecloud").imageCloud(); });