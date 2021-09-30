import {ng, notify, idiom} from 'entcore';
import {Utils} from "../utils/Utils";
import http from "axios";

interface ViewModel {
    files: File[];
    logs: string[];

    $onInit(): Promise<void>;
    $onDestroy(): Promise<void>;
    goMenu(): Promise<void>;
    convertFiles(): Promise<void>;
}


export const converterController = ng.controller('ConverterController', ['$scope',
    function ($scope) {
        const vm: ViewModel = this;
        vm.files = [];
        vm.logs = [];

        vm.$onInit = async (): Promise<void> => {
            await Utils.safeApply($scope);
        };

        // Functions

        vm.goMenu = async () : Promise<void> => {
            $scope.switchTab('dashboard');
        };

        vm.convertFiles = async () : Promise<void> => {
            try {
                // TODO format files to FormData format ??
                // let filesFormatted = [];
                // for (let i = 0; i < vm.files.length; i++) {
                //     let file = new FormData();
                //     filesFormatted.push(file.append("file", vm.files[i], vm.files[i].name));
                // }
                // let logs = http.post('/moodle/convert', filesFormatted);

                let { data } = await http.post('/moodle/convert', vm.files);
                vm.logs = data;
            } catch (err) {
                notify.error(idiom.translate('moodle.convert.error.convert'));
                throw err;
            }
        };
    }]);