import {ng} from "entcore";
import {Utils} from "../../utils/Utils";

export const renameFolderController = ng.controller('renameFolderController', ['$scope', '$timeout',
    ($scope, $timeout) => {

        $scope.renameFolder = async function () {
            $scope.folders.all.filter(folder => folder.select).forEach(async function (folder) {
                await folder.rename();
            });
            $scope.show.lightboxes = false;
            Utils.safeApply($scope);
            $timeout(() =>
                    $scope.initFolders()
                , 1500);
        };

    }]);
