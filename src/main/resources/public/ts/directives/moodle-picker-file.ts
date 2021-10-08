import {ng, template, Document, $, notify, idiom} from 'entcore';

export const moodlePickerFile = ng.directive('moodlePickerFile', () => {
    return {
        restrict: 'E',
        scope: {
            files: '=',
            multiple: '@'
        },
        template: `
			<div show="display.listFiles">
				<div class="media-library file-picker">
                    <container template="pick" class="six twelve-mobile"></container>
                    <container template="list" class="six twelve-mobile" ng-if="filesArray.length > 0" 
                    style="height: 200px; overflow-y: scroll;"></container>
                    <div class="six twelve-mobile no-list" ng-if="filesArray.length == 0"><i18n>moodle.convert.files.none</i18n></div>
				</div>
			</div>
		`,
        link: (scope, element, attributes) => {
            template.open('pick', 'entcore/file-picker-list/pick');

            if(!scope.display) {
                scope.display = {};
            }

            scope.picked = {};
            scope.filesArray = [];

            $('body').on('dragenter', '.icons-view', (e) => e.preventDefault());
            $('body').on('dragover', '.icons-view', (e) => e.preventDefault());
            element.on('dragenter', (e) => e.preventDefault());

            element.on('dragover', (e) => {
                element.find('.drop-zone').addClass('dragover');
                e.preventDefault();
            });

            element.on('dragleave', () => {
                element.find('.drop-zone').removeClass('dragover');
            });

            const dropFiles = (e) => {
                if (!e.originalEvent.dataTransfer.files.length){
                    return;
                }
                element.find('.drop-zone').removeClass('dragover');
                e.preventDefault();
                scope.listFiles(e.originalEvent.dataTransfer.files);
                scope.$apply();
            }

            scope.listFiles = function(files) {
                let shouldNotify = false;
                if (!files){
                    files = scope.picked.files;
                }
                for (let i = 0; i < files.length; i++) {
                    if (files[i].type.search("zip") >= 0) {
                        scope.filesArray.push(files[i]);
                    }
                    else {
                        shouldNotify = true;
                    }
                }
                scope.files = scope.filesArray;
                if (shouldNotify) {
                    notify.info(idiom.translate('moodle.convert.error.format'));
                }
                if (scope.filesArray && scope.filesArray.length > 0){
                    template.open('list', 'entcore/file-picker-list/list');
                }
                scope.$apply();
            }

            $('body').on('drop', '.icons-view', dropFiles);
            element.on('drop', dropFiles);

            scope.delete = (file: File) => {
                scope.filesArray = scope.filesArray.filter(f => f != file);
                scope.files = scope.filesArray;
                if(scope.filesArray && scope.filesArray.length <= 0){
                    template.close('list');
                }
            };

            scope.getIconClass = (contentType) => {
                return Document.role(contentType);
            }

            scope.getSizeHumanReadable = (size) => {
                const koSize = size / 1024;
                if (koSize > 1024) {
                    return (koSize / 1024 * 10 / 10)  + ' Mo';
                }
                return Math.ceil(koSize) + ' Ko';
            }
        }
    }
})