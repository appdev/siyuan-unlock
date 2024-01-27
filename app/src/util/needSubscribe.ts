import { showMessage } from "../dialog/message";
import { getCloudURL } from "../config/util/about";

export const needSubscribe = (tip = window.siyuan.languages._kernel[29]) => {
    return false;
};

export const isPaidUser = () => {
    return true;
};
