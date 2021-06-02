import {ng, template} from "entcore";
import {Utils} from "../../utils/Utils";

export const createFolderController = ng.controller('createFolderController', ['$scope',
    ($scope) => {
        $scope.createFolder = async function () {
            $scope.folder.parent_id = $scope.show.currentFolderId;
            if (!$scope.show.isCreating) {
                $scope.show.isCreating = true;
                await Utils.safeApply($scope);
                let status = await $scope.folder.create();
                if (status === 200) {
                    template.close('lightboxContainer')
                    $scope.show.lightboxes = false;
                }
                await $scope.initFolders();
            }
            await Utils.safeApply($scope);
        };
    }]);
