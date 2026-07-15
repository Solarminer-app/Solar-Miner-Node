import type {ComponentProps} from 'react';
import {Link as RouterLink} from 'react-router-dom';

type LinkProps = Omit<ComponentProps<typeof RouterLink>, 'to'> & {
    href: string;
};

export default function Link({href, ...props}: LinkProps) {
    return <RouterLink {...props} to={href}/>;
}
