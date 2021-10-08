import {ng, notify, idiom} from 'entcore';
import {Utils} from "../utils/Utils";
import http, {AxiosResponse} from "axios";

interface ViewModel {
    files: File[];
    logs: string;

    $onInit(): Promise<void>;
    $onDestroy(): Promise<void>;
    goMenu(): Promise<void>;
    convertFiles(): Promise<void>;
}


export const converterController = ng.controller('ConverterController', ['$scope',
    function ($scope) {
        const vm: ViewModel = this;
        vm.files = [];
        vm.logs = "";

        vm.$onInit = async (): Promise<void> => {
            await Utils.safeApply($scope);
        };

        // Functions

        vm.goMenu = async () : Promise<void> => {
            $scope.switchTab('dashboard');
        };

        vm.convertFiles = async () : Promise<void> => {
            try {
                for (let i = 0; i < vm.files.length; i++) {
                    let file = new FormData();
                    file.append("file", vm.files[i], vm.files[i].name);
                    await http.post("moodle/convert", file,{'headers' : { 'Content-Type': 'multipart/form-data' }})
                        .then((resp: AxiosResponse) => {
                            const data = resp.data;
                            const xml = data.xml;
                            const message = JSON.parse(data.message);
                            for (let l = 0; l < message.length; l++){
                                vm.logs += message[l]
                                vm.logs += "\n"
                            }
                            var filename = vm.files[i].name.replace(".zip","") + "_MoodleFormat.xml";
                            var pom = document.createElement('a');
                            var bb = new Blob([xml], {type: 'text/plain'});

                            pom.setAttribute('href', window.URL.createObjectURL(bb));
                            pom.setAttribute('download', filename);

                            pom.dataset.downloadurl = ['application/octet-stream', pom.download, pom.href].join(':');
                            pom.draggable = true;
                            pom.classList.add('dragout');

                            pom.click();
                            pom.remove();
                        });
                }
                await Utils.safeApply($scope);
            } catch (err) {
                notify.error(idiom.translate('moodle.convert.error.convert'));
                throw err;
            }
        };
    }]);